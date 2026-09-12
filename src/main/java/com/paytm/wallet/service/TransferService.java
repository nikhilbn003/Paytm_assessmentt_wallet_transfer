package com.paytm.wallet.service;

import com.paytm.wallet.config.DomainMetrics;
import com.paytm.wallet.domain.Transfer;
import com.paytm.wallet.dto.TransferRequest;
import com.paytm.wallet.exception.BadRequestException;
import com.paytm.wallet.exception.IdempotencyConflictException;
import com.paytm.wallet.exception.NotFoundException;
import com.paytm.wallet.repository.TransferRepository;
import com.paytm.wallet.repository.WalletRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import static net.logstash.logback.argument.StructuredArguments.kv;

/**
 * The core of the exercise.
 * <p>
 * Conservation + no-overdraft (Gate 3): an atomic conditional debit
 * ({@code UPDATE wallets SET balance = balance - :amt WHERE id = :id AND balance >= :amt}) is
 * the primitive; rows-affected = 0 means "would overdraw" and the transfer is declined cleanly,
 * with no partial application. We additionally take {@code SELECT ... FOR UPDATE} row locks on
 * both wallets, in ascending-id order, before touching them - this isn't strictly required by the
 * conditional UPDATE alone (which is already atomic per-row), but it serializes the debit+credit
 * pair for a given transfer against any concurrent transfer touching the same wallet, and the
 * sorted order is what prevents a classic deadlock when A-&gt;B and B-&gt;A run at the same instant
 * and would otherwise lock rows in opposite orders.
 * <p>
 * Exactly-once (Gate 2): the idempotency_key uniqueness (a UNIQUE constraint on
 * transfers.idempotency_key) is reserved via plain INSERT in the SAME transaction as the
 * debit/credit that follows - never checked-then-inserted separately. A concurrent duplicate's
 * INSERT blocks on the unique index until the first transaction commits (Postgres row-lock
 * behaviour on a unique index), then fails with a unique violation once the winner is visible -
 * so by the time a "loser" re-reads the existing transfer, it is guaranteed to see the final,
 * committed result, never a half-applied one. Same key + different body -&gt; 409.
 */
@Service
public class TransferService {

    private static final Logger log = LoggerFactory.getLogger(TransferService.class);

    private final WalletRepository walletRepository;
    private final TransferRepository transferRepository;
    private final DomainMetrics metrics;

    public TransferService(WalletRepository walletRepository, TransferRepository transferRepository,
                            DomainMetrics metrics) {
        this.walletRepository = walletRepository;
        this.transferRepository = transferRepository;
        this.metrics = metrics;
    }

    @Transactional
    public Transfer transfer(TransferRequest request) {
        UUID fromId = parseWalletId(request.from(), "from");
        UUID toId = parseWalletId(request.to(), "to");
        if (fromId.equals(toId)) {
            throw new BadRequestException("from and to must be different wallets");
        }
        if (walletRepository.findById(fromId).isEmpty()) {
            throw new NotFoundException("wallet " + fromId + " not found");
        }
        if (walletRepository.findById(toId).isEmpty()) {
            throw new NotFoundException("wallet " + toId + " not found");
        }

        String requestHash = hash(fromId, toId, request.amountPaise());
        UUID transferId = UUID.randomUUID();

        Optional<UUID> reserved = transferRepository.tryReserve(
                transferId, fromId, toId, request.amountPaise(), request.idempotencyKey(), requestHash);

        if (reserved.isEmpty()) {
            // Another request (this one or a concurrent duplicate) already holds this key.
            // Postgres's unique-index lock guarantees the row we read here is fully committed.
            Transfer existing = transferRepository.findByIdempotencyKey(request.idempotencyKey())
                    .orElseThrow(() -> new IllegalStateException("idempotency key vanished after conflict"));

            if (!existing.requestHash().equals(requestHash)) {
                metrics.transferIdempotencyConflict();
                log.warn("idempotency key reused with different body",
                        kv("event", "idempotency_conflict"), kv("idempotencyKey", request.idempotencyKey()));
                throw new IdempotencyConflictException(request.idempotencyKey());
            }

            metrics.transferIdempotentReplay();
            log.info("idempotent replay hit - returning original result",
                    kv("event", "idempotent_replay_hit"),
                    kv("idempotencyKey", request.idempotencyKey()),
                    kv("transferId", existing.id()));
            return existing;
        }

        log.info("transfer created",
                kv("event", "transfer_created"), kv("transferId", transferId),
                kv("fromWalletId", fromId), kv("toWalletId", toId), kv("amountPaise", request.amountPaise()));

        // Deterministic sorted lock order (lower UUID first) avoids A<->B deadlock.
        UUID first = fromId.compareTo(toId) <= 0 ? fromId : toId;
        UUID second = fromId.compareTo(toId) <= 0 ? toId : fromId;
        walletRepository.lockForUpdate(first);
        walletRepository.lockForUpdate(second);

        int debited = walletRepository.conditionalDebit(fromId, request.amountPaise());
        if (debited == 0) {
            transferRepository.markDeclined(transferId, "insufficient_funds");
            metrics.transferDeclinedInsufficientFunds();
            log.info("transfer declined: insufficient funds",
                    kv("event", "transfer_declined_insufficient_funds"), kv("transferId", transferId),
                    kv("fromWalletId", fromId));
            return transferRepository.findById(transferId).orElseThrow();
        }

        log.info("wallet debited",
                kv("event", "wallet_debited"), kv("transferId", transferId), kv("walletId", fromId),
                kv("amountPaise", request.amountPaise()));

        walletRepository.credit(toId, request.amountPaise());
        log.info("wallet credited",
                kv("event", "wallet_credited"), kv("transferId", transferId), kv("walletId", toId),
                kv("amountPaise", request.amountPaise()));

        transferRepository.markCompleted(transferId);
        metrics.transferCreated();
        log.info("transfer completed",
                kv("event", "transfer_completed"), kv("transferId", transferId));

        return transferRepository.findById(transferId).orElseThrow();
    }

    public Transfer getById(UUID id) {
        return transferRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("transfer " + id + " not found"));
    }

    private UUID parseWalletId(String raw, String field) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("'" + field + "' is not a valid wallet id");
        }
    }

    private String hash(UUID fromId, UUID toId, long amountPaise) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String canonical = fromId + "|" + toId + "|" + amountPaise;
            byte[] out = digest.digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(out);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}

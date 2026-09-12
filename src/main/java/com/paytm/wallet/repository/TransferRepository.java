package com.paytm.wallet.repository;

import com.paytm.wallet.domain.Transfer;
import com.paytm.wallet.domain.TransferStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class TransferRepository {

    private final JdbcTemplate jdbc;

    public TransferRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<Transfer> MAPPER = (rs, rowNum) -> new Transfer(
            UUID.fromString(rs.getString("id")),
            UUID.fromString(rs.getString("from_wallet_id")),
            UUID.fromString(rs.getString("to_wallet_id")),
            rs.getLong("amount_paise"),
            rs.getString("idempotency_key"),
            rs.getString("request_hash"),
            TransferStatus.valueOf(rs.getString("status")),
            rs.getString("decline_reason"),
            rs.getObject("created_at", java.time.OffsetDateTime.class),
            rs.getObject("updated_at", java.time.OffsetDateTime.class)
    );

    public Optional<Transfer> findByIdempotencyKey(String idempotencyKey) {
        return jdbc.query(
                "SELECT * FROM transfers WHERE idempotency_key = ?",
                MAPPER, idempotencyKey
        ).stream().findFirst();
    }

    public Optional<Transfer> findById(UUID id) {
        return jdbc.query("SELECT * FROM transfers WHERE id = ?", MAPPER, id).stream().findFirst();
    }

    /**
     * Reserves the idempotency key in the SAME transaction as the debit/credit that follows
     * (Gate 2). Uses INSERT ... ON CONFLICT DO NOTHING rather than a plain INSERT + catch: a
     * plain unique-violation would abort the whole Postgres transaction (nothing further could
     * run in it), whereas ON CONFLICT DO NOTHING still blocks on the unique index until any
     * concurrent transaction holding the same key finishes, but resolves to a harmless no-op
     * instead of an error - letting this same transaction continue on to read back the
     * (by-then-committed) winning row. Returns the newly-created PENDING transfer id if this
     * caller won the race, or empty if another caller already holds this key.
     */
    public Optional<UUID> tryReserve(UUID id, UUID fromWalletId, UUID toWalletId, long amountPaise,
                                      String idempotencyKey, String requestHash) {
        int inserted = jdbc.update("""
                INSERT INTO transfers (id, from_wallet_id, to_wallet_id, amount_paise,
                                        idempotency_key, request_hash, status)
                VALUES (?, ?, ?, ?, ?, ?, 'PENDING')
                ON CONFLICT (idempotency_key) DO NOTHING
                """, id, fromWalletId, toWalletId, amountPaise, idempotencyKey, requestHash);
        return inserted == 1 ? Optional.of(id) : Optional.empty();
    }

    public void markCompleted(UUID id) {
        jdbc.update("UPDATE transfers SET status = 'COMPLETED', updated_at = now() WHERE id = ?", id);
    }

    public void markDeclined(UUID id, String reason) {
        jdbc.update("UPDATE transfers SET status = 'DECLINED', decline_reason = ?, updated_at = now() WHERE id = ?",
                reason, id);
    }
}

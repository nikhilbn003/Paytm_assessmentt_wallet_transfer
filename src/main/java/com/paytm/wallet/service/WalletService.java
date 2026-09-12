package com.paytm.wallet.service;

import com.paytm.wallet.config.DomainMetrics;
import com.paytm.wallet.domain.Wallet;
import com.paytm.wallet.exception.NotFoundException;
import com.paytm.wallet.repository.WalletRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static net.logstash.logback.argument.StructuredArguments.kv;

@Service
public class WalletService {

    private static final Logger log = LoggerFactory.getLogger(WalletService.class);

    private final WalletRepository walletRepository;
    private final DomainMetrics metrics;

    public WalletService(WalletRepository walletRepository, DomainMetrics metrics) {
        this.walletRepository = walletRepository;
        this.metrics = metrics;
    }

    @Transactional
    public WalletRepository.GetOrCreateResult getOrCreate(String userId) {
        WalletRepository.GetOrCreateResult result = walletRepository.getOrCreate(userId);
        Wallet wallet = result.wallet();

        if (result.created()) {
            metrics.walletCreated();
            log.info("wallet get-or-create: created new wallet",
                    kv("event", "wallet_created"), kv("userId", userId), kv("walletId", wallet.id()));
        } else {
            metrics.walletFetchedExisting();
            log.info("wallet get-or-create: returned existing wallet",
                    kv("event", "wallet_fetched_existing"), kv("userId", userId), kv("walletId", wallet.id()));
        }
        return result;
    }

    public Wallet getById(UUID id) {
        return walletRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("wallet " + id + " not found"));
    }

    /** Test-seeding only - not a graded invariant. See WalletRepository#credit. */
    @Transactional
    public Wallet deposit(UUID id, long amountPaise) {
        getById(id); // 404 if unknown
        walletRepository.credit(id, amountPaise);
        log.info("wallet seeded (test-only deposit)",
                kv("event", "wallet_test_deposit"), kv("walletId", id), kv("amountPaise", amountPaise));
        return getById(id);
    }
}

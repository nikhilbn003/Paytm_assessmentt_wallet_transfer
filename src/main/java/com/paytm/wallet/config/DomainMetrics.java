package com.paytm.wallet.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Domain counters requested by the exercise: transfers created / declined-insufficient-funds /
 * idempotent-replays, plus wallet creation. Exposed via /actuator/prometheus.
 */
@Component
public class DomainMetrics {

    private final Counter transfersCreated;
    private final Counter transfersDeclinedInsufficientFunds;
    private final Counter transfersIdempotentReplays;
    private final Counter transfersConflict;
    private final Counter walletsCreated;
    private final Counter walletsFetchedExisting;

    public DomainMetrics(MeterRegistry registry) {
        this.transfersCreated = Counter.builder("wallet_transfers_created_total")
                .description("Transfers that completed successfully")
                .register(registry);
        this.transfersDeclinedInsufficientFunds = Counter.builder("wallet_transfers_declined_insufficient_funds_total")
                .description("Transfers declined due to insufficient funds")
                .register(registry);
        this.transfersIdempotentReplays = Counter.builder("wallet_transfers_idempotent_replays_total")
                .description("Requests that hit an existing idempotency key and were replayed")
                .register(registry);
        this.transfersConflict = Counter.builder("wallet_transfers_idempotency_conflicts_total")
                .description("Requests with a reused idempotency key but a different body (409)")
                .register(registry);
        this.walletsCreated = Counter.builder("wallet_wallets_created_total")
                .description("Brand-new wallets created")
                .register(registry);
        this.walletsFetchedExisting = Counter.builder("wallet_wallets_fetched_existing_total")
                .description("get-or-create calls that returned an already-existing wallet")
                .register(registry);
    }

    public void transferCreated() { transfersCreated.increment(); }
    public void transferDeclinedInsufficientFunds() { transfersDeclinedInsufficientFunds.increment(); }
    public void transferIdempotentReplay() { transfersIdempotentReplays.increment(); }
    public void transferIdempotencyConflict() { transfersConflict.increment(); }
    public void walletCreated() { walletsCreated.increment(); }
    public void walletFetchedExisting() { walletsFetchedExisting.increment(); }
}

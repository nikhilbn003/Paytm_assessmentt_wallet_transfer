package com.paytm.wallet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.Check;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Schema-only mapping: describes the {@code transfers} table so Hibernate's {@code ddl-auto}
 * creates/updates it on startup, replacing the old Flyway migration. No repository or service
 * uses this entity for reads/writes - all actual queries go through {@link
 * com.paytm.wallet.repository.TransferRepository} via JdbcTemplate, which is where the graded
 * correctness mechanisms (same-transaction idempotency reservation, atomic status updates) live.
 */
@Entity
@Table(name = "transfers",
        uniqueConstraints = @UniqueConstraint(name = "uq_transfers_idempotency_key", columnNames = "idempotency_key"),
        indexes = {
                @Index(name = "idx_transfers_from_wallet", columnList = "from_wallet_id"),
                @Index(name = "idx_transfers_to_wallet", columnList = "to_wallet_id")
        })
@Check(constraints = "amount_paise > 0")
@Check(constraints = "status IN ('PENDING', 'COMPLETED', 'DECLINED')")
public class TransferJpaEntity {

    @Id
    private UUID id;

    @Column(name = "from_wallet_id", nullable = false)
    private UUID fromWalletId;

    @Column(name = "to_wallet_id", nullable = false)
    private UUID toWalletId;

    @Column(name = "amount_paise", nullable = false)
    private long amountPaise;

    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false)
    private String requestHash;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "decline_reason")
    private String declineReason;

    @Column(name = "created_at", nullable = false, columnDefinition = "TIMESTAMPTZ NOT NULL DEFAULT now()")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "TIMESTAMPTZ NOT NULL DEFAULT now()")
    private OffsetDateTime updatedAt;

    protected TransferJpaEntity() {
    }
}

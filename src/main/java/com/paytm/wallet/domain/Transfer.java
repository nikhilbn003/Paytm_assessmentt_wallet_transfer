package com.paytm.wallet.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

public record Transfer(
        UUID id,
        UUID fromWalletId,
        UUID toWalletId,
        long amountPaise,
        String idempotencyKey,
        String requestHash,
        TransferStatus status,
        String declineReason,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}

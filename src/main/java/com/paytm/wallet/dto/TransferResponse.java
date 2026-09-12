package com.paytm.wallet.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.paytm.wallet.domain.Transfer;

public record TransferResponse(
        String id,
        String from,
        String to,
        @JsonProperty("amount_paise") long amountPaise,
        @JsonProperty("idempotency_key") String idempotencyKey,
        String status,
        @JsonProperty("decline_reason") String declineReason
) {
    public static TransferResponse from(Transfer t) {
        return new TransferResponse(
                t.id().toString(),
                t.fromWalletId().toString(),
                t.toWalletId().toString(),
                t.amountPaise(),
                t.idempotencyKey(),
                t.status().name(),
                t.declineReason()
        );
    }
}

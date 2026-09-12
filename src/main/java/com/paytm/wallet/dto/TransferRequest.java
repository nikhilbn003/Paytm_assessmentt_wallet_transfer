package com.paytm.wallet.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record TransferRequest(
        @NotBlank String from,
        @NotBlank String to,
        @JsonProperty("amount_paise") @Positive long amountPaise,
        @NotBlank @JsonProperty("idempotency_key") String idempotencyKey
) {
}

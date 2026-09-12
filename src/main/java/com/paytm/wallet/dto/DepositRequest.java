package com.paytm.wallet.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Positive;

/** Test-seeding only - see WalletRepository#credit. */
public record DepositRequest(
        @JsonProperty("amount_paise") @Positive long amountPaise
) {
}

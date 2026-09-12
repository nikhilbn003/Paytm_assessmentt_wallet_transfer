package com.paytm.wallet.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.paytm.wallet.domain.Wallet;

public record WalletResponse(
        String id,
        @JsonProperty("user_id") String userId,
        @JsonProperty("balance_paise") long balancePaise
) {
    public static WalletResponse from(Wallet w) {
        return new WalletResponse(w.id().toString(), w.userId(), w.balancePaise());
    }
}

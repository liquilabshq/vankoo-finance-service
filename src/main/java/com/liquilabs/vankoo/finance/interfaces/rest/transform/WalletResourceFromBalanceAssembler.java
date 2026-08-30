package com.liquilabs.vankoo.finance.interfaces.rest.transform;

import com.liquilabs.vankoo.finance.domain.model.queries.WalletBalance;
import com.liquilabs.vankoo.finance.interfaces.rest.resources.WalletResource;

/** Assemblers are static, like the guide's {@code BookCargoCommandDTOAssembler}. */
public final class WalletResourceFromBalanceAssembler {

    private WalletResourceFromBalanceAssembler() {
    }

    public static WalletResource toResourceFromBalance(WalletBalance balance) {
        return new WalletResource(
                balance.walletId().toString(),
                balance.accountId().toString(),
                balance.balance().currency().name(),
                balance.balance().amountMinor(),
                balance.createdAt().toString(),
                balance.updatedAt().toString());
    }
}

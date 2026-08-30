package com.liquilabs.vankoo.finance.interfaces.rest.transform;

import com.liquilabs.vankoo.finance.domain.model.queries.WalletMovementPage;
import com.liquilabs.vankoo.finance.interfaces.rest.resources.WalletMovementPageResource;

/** Assemblers are static, like the guide's {@code BookCargoCommandDTOAssembler}. */
public final class WalletMovementPageResourceFromPageAssembler {

    private WalletMovementPageResourceFromPageAssembler() {
    }

    public static WalletMovementPageResource toResourceFromPage(WalletMovementPage page) {
        return new WalletMovementPageResource(
                page.items().stream().map(WalletMovementResourceFromMovementAssembler::toResourceFromMovement).toList(),
                page.pageNumber(),
                page.pageSize(),
                page.totalElements());
    }
}

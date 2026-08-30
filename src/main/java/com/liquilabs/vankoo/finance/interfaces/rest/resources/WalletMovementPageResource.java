package com.liquilabs.vankoo.finance.interfaces.rest.resources;

import java.util.List;

/** Response shape for the paginated wallet movement history endpoint. */
public record WalletMovementPageResource(
        List<WalletMovementResource> items,
        int pageNumber,
        int pageSize,
        long totalElements) {
}

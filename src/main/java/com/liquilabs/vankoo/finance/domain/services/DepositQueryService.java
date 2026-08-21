package com.liquilabs.vankoo.finance.domain.services;

import com.liquilabs.vankoo.finance.domain.model.queries.DepositSummary;
import com.liquilabs.vankoo.finance.domain.model.queries.DepositSummaryPage;
import com.liquilabs.vankoo.finance.domain.model.queries.GetDepositByIdQuery;
import com.liquilabs.vankoo.finance.domain.model.queries.ListDepositsByAccountQuery;

import java.util.Optional;

/**
 * The Query Model's contract. The domain declares it; {@code application}'s
 * projection implements it — same split as {@code DepositCommandService}.
 *
 * <p>Answers are read from the Read Model, never from the aggregate: it is
 * never rehydrated from the Event Store to serve a query.
 */
public interface DepositQueryService {

    Optional<DepositSummary> getDepositById(GetDepositByIdQuery query);

    DepositSummaryPage listDepositsByAccount(ListDepositsByAccountQuery query);
}

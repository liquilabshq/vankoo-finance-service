package com.liquilabs.vankoo.finance.domain.services;

import com.liquilabs.vankoo.finance.domain.model.queries.GetWalletBalanceQuery;
import com.liquilabs.vankoo.finance.domain.model.queries.ListWalletMovementsQuery;
import com.liquilabs.vankoo.finance.domain.model.queries.WalletBalance;
import com.liquilabs.vankoo.finance.domain.model.queries.WalletMovementPage;

import java.util.Optional;

/**
 * The Query Model's contract for {@code Wallet}. The domain declares it;
 * {@code application}'s projection implements it — same split as
 * {@code DepositQueryService}.
 *
 * <p>Answers are read from the Read Model, never from the aggregate: it is
 * never rehydrated from the Event Store to serve a query.
 *
 * <p>A wallet that was never opened (the investor has not deposited in that
 * currency yet — {@code Wallet} is created lazily) is not an error: both
 * methods answer with an empty result, never an exception.
 */
public interface WalletQueryService {

    Optional<WalletBalance> getWalletBalance(GetWalletBalanceQuery query);

    WalletMovementPage listWalletMovements(ListWalletMovementsQuery query);
}

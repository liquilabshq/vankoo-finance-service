package com.liquilabs.vankoo.finance.application.internal.outboundservices.paymentprovider.model;

/**
 * The Finance status taxonomy that adapters translate provider observations
 * into, before any command is issued.
 *
 * <p>Mirrors the «Mapeo de proveedores externos» table in the contract. It does
 * not include {@code PENDING}: that is the initial status the aggregate gives
 * itself when it accepts the deposit, and it never comes from an external
 * observation.
 *
 * <p>PROVISIONAL: pending review with Salim. Once the domain's
 * {@code DepositStatus} exists, we must decide whether this enum merges into it
 * or stays separate — staying separate lets the provider taxonomy evolve
 * without touching the domain, at the cost of an explicit mapping.
 */
public enum NormalizedDepositStatus {
    ACTION_REQUIRED,
    PROCESSING,
    SUCCEEDED,
    FAILED,
    CANCELLED
}

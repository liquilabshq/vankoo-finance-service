package com.liquilabs.vankoo.finance.infrastructure.persistence.jpa.converters;

import com.liquilabs.vankoo.finance.domain.model.valueobjects.AccountId;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.UUID;

/**
 * Maps {@link AccountId} onto a native {@code uuid} column, same pattern as
 * {@link DepositIdConverter}.
 */
@Converter
public class AccountIdConverter implements AttributeConverter<AccountId, UUID> {

    @Override
    public UUID convertToDatabaseColumn(AccountId attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public AccountId convertToEntityAttribute(UUID dbData) {
        return dbData == null ? null : new AccountId(dbData);
    }
}

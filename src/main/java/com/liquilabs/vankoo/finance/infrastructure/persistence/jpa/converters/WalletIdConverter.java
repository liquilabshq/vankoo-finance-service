package com.liquilabs.vankoo.finance.infrastructure.persistence.jpa.converters;

import com.liquilabs.vankoo.finance.domain.model.valueobjects.WalletId;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.UUID;

/**
 * Maps {@link WalletId} onto a native {@code uuid} column, same pattern as
 * {@link DepositIdConverter}.
 */
@Converter
public class WalletIdConverter implements AttributeConverter<WalletId, UUID> {

    @Override
    public UUID convertToDatabaseColumn(WalletId attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public WalletId convertToEntityAttribute(UUID dbData) {
        return dbData == null ? null : new WalletId(dbData);
    }
}

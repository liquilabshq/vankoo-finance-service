package com.liquilabs.vankoo.finance.infrastructure.persistence.jpa.converters;

import com.liquilabs.vankoo.finance.domain.model.valueobjects.ProviderDepositId;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Maps {@link ProviderDepositId} onto a {@code varchar} column.
 *
 * <p>Stored verbatim: the provider's identifier is opaque, so it is neither
 * parsed nor normalized on the way in or out.
 */
@Converter
public class ProviderDepositIdConverter implements AttributeConverter<ProviderDepositId, String> {

    @Override
    public String convertToDatabaseColumn(ProviderDepositId attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public ProviderDepositId convertToEntityAttribute(String dbData) {
        return dbData == null ? null : new ProviderDepositId(dbData);
    }
}

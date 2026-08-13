package com.liquilabs.vankoo.finance.infrastructure.persistence.jpa.converters;

import com.liquilabs.vankoo.finance.domain.model.valueobjects.ProviderEventId;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Maps {@link ProviderEventId} onto a {@code varchar} column.
 *
 * <p>Stored verbatim, like {@link ProviderDepositIdConverter}: it is the
 * provider's own identifier and the deduplication key of the webhook inbox.
 */
@Converter
public class ProviderEventIdConverter implements AttributeConverter<ProviderEventId, String> {

    @Override
    public String convertToDatabaseColumn(ProviderEventId attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public ProviderEventId convertToEntityAttribute(String dbData) {
        return dbData == null ? null : new ProviderEventId(dbData);
    }
}

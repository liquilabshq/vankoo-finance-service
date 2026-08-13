package com.liquilabs.vankoo.finance.infrastructure.persistence.jpa.converters;

import com.liquilabs.vankoo.finance.domain.model.valueobjects.DepositId;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.UUID;

/**
 * Maps {@link DepositId} onto a native {@code uuid} column.
 *
 * <p>The mapping is declared once, here, so the domain value objects carry no
 * JPA annotation. Storing the UUID natively — not as {@code text} — is what
 * keeps the UUIDv7 insert locality the contract asks for.
 */
@Converter
public class DepositIdConverter implements AttributeConverter<DepositId, UUID> {

    @Override
    public UUID convertToDatabaseColumn(DepositId attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public DepositId convertToEntityAttribute(UUID dbData) {
        return dbData == null ? null : new DepositId(dbData);
    }
}

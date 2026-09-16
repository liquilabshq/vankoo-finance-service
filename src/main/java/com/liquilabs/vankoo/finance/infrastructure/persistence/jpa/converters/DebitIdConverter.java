package com.liquilabs.vankoo.finance.infrastructure.persistence.jpa.converters;

import com.liquilabs.vankoo.finance.domain.model.valueobjects.DebitId;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.UUID;

/**
 * Maps {@link DebitId} onto a native {@code uuid} column — same reasoning as
 * {@link DepositIdConverter}: the domain value object stays free of JPA, and
 * the UUIDv7 keeps its insert locality by not being stored as text.
 */
@Converter
public class DebitIdConverter implements AttributeConverter<DebitId, UUID> {

    @Override
    public UUID convertToDatabaseColumn(DebitId attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public DebitId convertToEntityAttribute(UUID dbData) {
        return dbData == null ? null : new DebitId(dbData);
    }
}

package com.liquilabs.vankoo.finance.infrastructure.persistence.jpa.converters;

import com.liquilabs.vankoo.finance.domain.model.valueobjects.AuctionId;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.UUID;

/** Maps {@link AuctionId} onto a native {@code uuid} column, like {@link DepositIdConverter}. */
@Converter
public class AuctionIdConverter implements AttributeConverter<AuctionId, UUID> {

    @Override
    public UUID convertToDatabaseColumn(AuctionId attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public AuctionId convertToEntityAttribute(UUID dbData) {
        return dbData == null ? null : new AuctionId(dbData);
    }
}

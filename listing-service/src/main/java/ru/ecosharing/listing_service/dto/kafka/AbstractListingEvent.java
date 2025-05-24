package ru.ecosharing.listing_service.dto.kafka;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder; // Для builder'а в дочерних классах

import java.time.Instant;
import java.util.UUID;

@Data
@SuperBuilder // Позволяет дочерним классам использовать builder с полями родителя
@NoArgsConstructor
@AllArgsConstructor
public abstract class AbstractListingEvent {
    private UUID eventId;        // Уникальный ID события
    private Instant eventTime;   // Время возникновения события
    private UUID listingId;      // ID объявления, к которому относится событие
    private String eventType;    // Тип события (например, "LISTING_CREATED", "LISTING_UPDATED")

    public AbstractListingEvent(UUID listingId, String eventType) {
        this.eventId = UUID.randomUUID();
        this.eventTime = Instant.now();
        this.listingId = listingId;
        this.eventType = eventType;
    }
}
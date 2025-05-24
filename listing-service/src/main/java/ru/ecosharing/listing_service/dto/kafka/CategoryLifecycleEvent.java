package ru.ecosharing.listing_service.dto.kafka;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.util.UUID;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryLifecycleEvent {
    private UUID eventId;
    private Instant eventTime;
    private UUID categoryId;
    private String eventType; // "CATEGORY_ACTIVATED", "CATEGORY_DEACTIVATED"
    private boolean isActive; // Новый статус активности

    public CategoryLifecycleEvent(UUID categoryId, String eventType, boolean isActive) {
        this.eventId = UUID.randomUUID();
        this.eventTime = Instant.now();
        this.categoryId = categoryId;
        this.eventType = eventType;
        this.isActive = isActive;
    }
}
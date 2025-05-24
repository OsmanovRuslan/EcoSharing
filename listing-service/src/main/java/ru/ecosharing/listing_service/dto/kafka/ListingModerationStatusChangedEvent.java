package ru.ecosharing.listing_service.dto.kafka;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import ru.ecosharing.listing_service.enums.ModerationStatus;

import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@NoArgsConstructor
public class ListingModerationStatusChangedEvent extends AbstractListingEvent {
    private ModerationStatus newModerationStatus;
    private ModerationStatus oldModerationStatus; // Опционально, для логики у консьюмера

    public ListingModerationStatusChangedEvent(UUID listingId, ModerationStatus newModerationStatus, ModerationStatus oldModerationStatus, UUID moderatorId) {
        super(listingId, "LISTING_MODERATION_STATUS_CHANGED");
        this.newModerationStatus = newModerationStatus;
        this.oldModerationStatus = oldModerationStatus;
    }
}
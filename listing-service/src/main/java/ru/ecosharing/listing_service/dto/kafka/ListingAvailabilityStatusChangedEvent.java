package ru.ecosharing.listing_service.dto.kafka;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import ru.ecosharing.listing_service.enums.AvailabilityStatus;

import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@NoArgsConstructor
public class ListingAvailabilityStatusChangedEvent extends AbstractListingEvent {
    private AvailabilityStatus newAvailabilityStatus;

    public ListingAvailabilityStatusChangedEvent(UUID listingId, AvailabilityStatus newAvailabilityStatus) {
        super(listingId, "LISTING_AVAILABILITY_STATUS_CHANGED");
        this.newAvailabilityStatus = newAvailabilityStatus;
    }
}
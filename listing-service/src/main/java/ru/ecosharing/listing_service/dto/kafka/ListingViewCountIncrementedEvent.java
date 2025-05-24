package ru.ecosharing.listing_service.dto.kafka;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@NoArgsConstructor
public class ListingViewCountIncrementedEvent extends AbstractListingEvent {
    private Integer newViewCount;

    public ListingViewCountIncrementedEvent(UUID listingId, Integer newViewCount) {
        super(listingId, "LISTING_VIEW_COUNT_INCREMENTED");
        this.newViewCount = newViewCount;
    }
}
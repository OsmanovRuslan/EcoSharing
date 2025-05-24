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
public class ListingDeletedEvent extends AbstractListingEvent {

    public ListingDeletedEvent(UUID listingId) {
        super(listingId, "LISTING_DELETED");
    }
}
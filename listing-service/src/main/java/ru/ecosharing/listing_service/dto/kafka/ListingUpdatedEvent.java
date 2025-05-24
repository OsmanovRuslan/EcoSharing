package ru.ecosharing.listing_service.dto.kafka;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import ru.ecosharing.listing_service.enums.PriceType;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@NoArgsConstructor
public class ListingUpdatedEvent extends AbstractListingEvent {
    private String title;
    private String description;
    private UUID categoryId;
    private String mainImageUrl;
    private List<String> additionalImageUrls;
    private String locationText;
    private BigDecimal price;
    private String currency;
    private PriceType priceType;


    public ListingUpdatedEvent(UUID listingId, String title, String description,
                               UUID categoryId, String mainImageUrl, List<String> additionalImageUrls,
                               String locationText, BigDecimal price, String currency, PriceType priceType) {
        super(listingId, "LISTING_UPDATED");
        this.title = title;
        this.description = description;
        this.categoryId = categoryId;
        this.mainImageUrl = mainImageUrl;
        this.additionalImageUrls = additionalImageUrls;
        this.locationText = locationText;
        this.price = price;
        this.currency = currency;
        this.priceType = priceType;
    }
}
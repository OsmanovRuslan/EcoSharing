package ru.ecosharing.listing_service.dto.kafka;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import ru.ecosharing.listing_service.enums.PriceType; // Импорт ваших Enums

import java.math.BigDecimal;
import java.time.LocalDateTime; // Используем LocalDateTime как в модели Listing
import java.util.List;
import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@NoArgsConstructor
public class ListingCreatedEvent extends AbstractListingEvent {
    private UUID userId;
    private String title;
    private String description;
    private UUID categoryId;
    private String mainImageUrl;
    private List<String> additionalImageUrls;
    private String locationText;
    private BigDecimal price;
    private String currency;
    private PriceType priceType;
    private LocalDateTime createdAt;

    public ListingCreatedEvent(UUID listingId, UUID userId, String title, String description,
                               UUID categoryId, String mainImageUrl, List<String> additionalImageUrls,
                               String locationText, BigDecimal price, String currency, PriceType priceType,
                               LocalDateTime createdAt) {
        super(listingId, "LISTING_CREATED");
        this.userId = userId;
        this.title = title;
        this.description = description;
        this.categoryId = categoryId;
        this.mainImageUrl = mainImageUrl;
        this.additionalImageUrls = additionalImageUrls;
        this.locationText = locationText;
        this.price = price;
        this.currency = currency;
        this.priceType = priceType;
        this.createdAt = createdAt;
    }
}
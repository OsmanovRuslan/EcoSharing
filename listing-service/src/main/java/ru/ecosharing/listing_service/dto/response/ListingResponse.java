package ru.ecosharing.listing_service.dto.response;

import lombok.Builder;
import lombok.Data;
import ru.ecosharing.listing_service.enums.AvailabilityStatus;
import ru.ecosharing.listing_service.enums.ModerationStatus;
import ru.ecosharing.listing_service.enums.PriceType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class ListingResponse {
    private UUID id;
    private UserSummaryDto owner;
    private String title;
    private String description;
    private CategoryResponse category; // Вложенный DTO для категории
    private String mainImageUrl;
    private List<String> additionalImageUrls;
    private String locationText;
    private BigDecimal price;
    private String currency;
    private PriceType priceType;
    private ModerationStatus moderationStatus;
    private AvailabilityStatus availabilityStatus;
    private String moderationComment; // Виден владельцу, если статус NEEDS_REVISION или REJECTED
    private String rejectionReason;
    private Integer viewCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime lastModeratedAt;
    private boolean isFavorite; // Для текущего пользователя, добавившего в избранное (вычисляется в сервисе)
}
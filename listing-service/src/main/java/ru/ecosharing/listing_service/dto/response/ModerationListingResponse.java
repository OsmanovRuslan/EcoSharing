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
public class ModerationListingResponse {
    private UUID id;
    private UserSummaryDto ownerDetails; // Детали владельца
    private String title;
    private String description;
    private CategoryResponse category;
    private String mainImageUrl;
    private List<String> additionalImageUrls;
    private String locationText;
    private BigDecimal price;
    private String currency;
    private PriceType priceType;
    private ModerationStatus moderationStatus;
    private AvailabilityStatus availabilityStatus;
    private String moderationComment; // Комментарий, оставленный ранее (если есть)
    private String rejectionReason; // Причина отклонения, оставленная ранее (если есть)
    private Integer viewCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime lastModeratedAt;
}
package ru.ecosharing.listing_service.dto.response;

import lombok.Builder;
import lombok.Data;
import ru.ecosharing.listing_service.enums.AvailabilityStatus;
import ru.ecosharing.listing_service.enums.PriceType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class ListingSummaryResponse {
    private UUID id;
    private String title;
    private String mainImageUrl;
    private String locationText;
    private BigDecimal price;
    private String currency;
    private PriceType priceType;
    private AvailabilityStatus availabilityStatus;
    private UUID categoryId; // Или просто ID категории
    private String categoryName;
    private LocalDateTime createdAt;
    private boolean isFavorite; // Для текущего пользователя
    private Integer viewCount; // Опционально, если нужно в списке
    private UUID ownerUserId; // ID владельца (для формирования ссылки на его профиль/объявления)
    private String ownerUsername;
}
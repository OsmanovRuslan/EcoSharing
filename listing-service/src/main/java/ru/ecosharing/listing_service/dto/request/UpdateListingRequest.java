package ru.ecosharing.listing_service.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;
import ru.ecosharing.listing_service.enums.PriceType;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
public class UpdateListingRequest {

    @Size(min = 5, max = 255, message = "Длина заголовка должна быть от 5 до 255 символов")
    private String title;

    @Size(min = 20, max = 5000, message = "Длина описания должна быть от 20 до 5000 символов")
    private String description;

    private UUID categoryId; // Можно изменить категорию

    @Size(max = 512, message = "URL основного изображения слишком длинный")
    private String mainImageUrl;

    @Size(max = 5, message = "Можно добавить не более 5 дополнительных изображений")
    private List<@Size(max = 512, message = "URL дополнительного изображения слишком длинный") String> additionalImageUrls;

    @Size(max = 512, message = "Текст местоположения слишком длинный")
    private String locationText;

    @DecimalMin(value = "0.0", inclusive = false)
    @Digits(integer = 15, fraction = 4)
    private BigDecimal price;

    @Size(min = 3, max = 3)
    private String currency;

    private PriceType priceType;

}
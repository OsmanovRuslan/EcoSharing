package ru.ecosharing.listing_service.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;
import ru.ecosharing.listing_service.enums.PriceType;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
public class CreateListingRequest {

    @NotBlank(message = "Заголовок не может быть пустым")
    @Size(min = 5, max = 255, message = "Длина заголовка должна быть от 5 до 255 символов")
    private String title;

    @NotBlank(message = "Описание не может быть пустым")
    @Size(min = 20, max = 5000, message = "Длина описания должна быть от 20 до 5000 символов")
    private String description;

    @NotNull(message = "ID категории не может быть null")
    private UUID categoryId;

    @Size(max = 512, message = "URL основного изображения слишком длинный")
    private String mainImageUrl;

    @Size(max = 5, message = "Можно добавить не более 5 дополнительных изображений")
    private List<@Size(max = 512, message = "URL дополнительного изображения слишком длинный") String> additionalImageUrls;

    @NotBlank(message = "Местоположение не может быть пустым")
    @Size(max = 512, message = "Текст местоположения слишком длинный")
    private String locationText;

    @NotNull(message = "Цена не может быть null")
    @DecimalMin(value = "0.0", inclusive = false, message = "Цена должна быть больше нуля") // или true, если 0 разрешен
    @Digits(integer = 15, fraction = 4, message = "Некорректный формат цены") // 15 цифр до точки, 4 после
    private BigDecimal price;

    @NotBlank(message = "Валюта не может быть пустой")
    @Size(min = 3, max = 3, message = "Код валюты должен состоять из 3 символов") // Например, RUB
    private String currency;

    @NotNull(message = "Тип цены не может быть null")
    private PriceType priceType;
}
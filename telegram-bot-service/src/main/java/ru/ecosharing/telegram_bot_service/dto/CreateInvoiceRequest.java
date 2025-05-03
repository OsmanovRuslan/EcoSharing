package ru.ecosharing.telegram_bot_service.dto;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Positive;
import javax.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO для запроса создания инвойса (ссылки для оплаты) через API.
 * Используется для инициации платежа Stars ("XTR") из Mini App.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateInvoiceRequest {

    /**
     * ID пользователя в ВАШЕЙ системе (опционально, для логирования).
     * Рекомендуется включать user_id также и в payload.
     */
    private String userId;

    /**
     * Уникальные данные для идентификации покупки после успешной оплаты.
     * Например: "type=subscription&period=MONTHLY&user_id=12345"
     *            "type=item&item_id=abc&user_id=12345"
     * ОБЯЗАТЕЛЬНО. Длина 1-128 байт.
     */
    @NotBlank(message = "Payload is required")
    @Size(min = 1, max = 128, message = "Payload must be between 1 and 128 bytes") // Примерная проверка длины
    private String payload;

    /**
     * Заголовок счета (макс. 32 символа). Будет виден пользователю.
     * ОБЯЗАТЕЛЬНО.
     */
    @NotBlank(message = "Title is required")
    @Size(max = 32, message = "Title cannot exceed 32 characters")
    private String title;

    /**
     * Описание счета (макс. 255 символов). Будет видно пользователю.
     * ОБЯЗАТЕЛЬНО.
     */
    @NotBlank(message = "Description is required")
    @Size(max = 255, message = "Description cannot exceed 255 characters")
    private String description;

    /**
     * Сумма в количестве Telegram Stars.
     * ОБЯЗАТЕЛЬНО. Должна быть положительной.
     * Минимальное/максимальное значение может определяться Telegram.
     */
    @NotNull(message = "Amount (stars) is required")
    @Positive(message = "Amount (stars) must be positive")
    // @Min(value = 1, message = "Minimum amount is 1 star") // Уточнить минимальное значение у Telegram
    // @Max(value = 2500, message = "Maximum amount is 2500 stars") // Уточнить максимальное значение у Telegram
    private Integer amount;

    /**
     * Код валюты. Для оплаты звездами должен быть "XTR".
     * ОБЯЗАТЕЛЬНО.
     */
    @NotBlank(message = "Currency is required")
    @Pattern(regexp = "^XTR$", message = "Currency must be 'XTR' for Stars payments")
    private String currency = "XTR"; // Устанавливаем по умолчанию

}
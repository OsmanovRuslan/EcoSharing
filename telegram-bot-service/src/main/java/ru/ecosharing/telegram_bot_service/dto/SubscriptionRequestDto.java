package ru.ecosharing.telegram_bot_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import ru.ecosharing.telegram_bot_service.model.SubscriptionPeriod;

/**
 * DTO для запроса активации подписки
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionRequestDto {

    /**
     * ID пользователя (может быть telegramId или userId в системе)
     */
    private String userId;

    /**
     * Период подписки
     */
    private SubscriptionPeriod period;

    /**
     * Сумма оплаты в копейках/центах
     */
    private Integer amount;

    /**
     * Валюта платежа
     */
    private String currency;

    /**
     * ID платежа в системе оплаты
     */
    private String paymentId;
}
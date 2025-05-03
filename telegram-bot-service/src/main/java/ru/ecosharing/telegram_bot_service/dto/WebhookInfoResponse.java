package ru.ecosharing.telegram_bot_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * DTO для ответа с информацией о вебхуке
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WebhookInfoResponse {

    /**
     * Успешность операции
     */
    private boolean success;

    /**
     * Сообщение об ошибке (если есть)
     */
    private String error;

    /**
     * Информация о вебхуке
     */
    private Map<String, Object> webhookInfo;
}
package ru.ecosharing.telegram_bot_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import ru.ecosharing.telegram_bot_service.model.NotificationType;

import java.util.HashMap;
import java.util.Map;

/**
 * DTO для запроса отправки уведомления
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NotificationRequest {

    /**
     * ID пользователя в Telegram
     */
    private String chatId;

    /**
     * Тип уведомления
     */
    private NotificationType notificationType;

    /**
     * Параметры для шаблона уведомления
     */
    private Map<String, String> params = new HashMap<>();

    /**
     * Добавлять ли кнопку для открытия WebApp
     */
    private boolean attachWebAppButton = false;
}
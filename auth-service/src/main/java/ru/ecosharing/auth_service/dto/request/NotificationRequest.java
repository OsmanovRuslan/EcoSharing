package ru.ecosharing.auth_service.dto.request;

import lombok.Data;
import ru.ecosharing.auth_service.dto.enums.NotificationType;

import java.util.Map;

/**
 * DTO для запроса на отправку уведомления через NotificationClient.
 */
@Data
public class NotificationRequest {
    private String chatId; // ID чата получателя (например, Telegram ID)
    private NotificationType notificationType; // Тип уведомления
    private Map<String, String> params; // Параметры для подстановки в шаблон сообщения
    private boolean attachWebAppButton; // Флаг: прикрепить ли кнопку для открытия WebApp
}
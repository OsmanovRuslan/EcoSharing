package ru.ecosharing.user_service.dto.response; // Укажи правильный пакет в user-service

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * DTO, возвращающий данные пользователя, необходимые Notification Service.
 * User Service НЕ хранит telegramId, поэтому это поле будет null,
 * и Notification Service должен будет получить его из Auth Service.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserNotificationDetailsDto {
    private UUID userId;
    private String email;        // Email пользователя (может быть null)
    private String firstName;    // Имя для персонализации (может быть null)
    private String language;     // Язык пользователя (например, "ru", "en", может быть null)
    private boolean emailNotificationsEnabled; // Глобальный флаг email уведомлений
    private boolean telegramNotificationsEnabled; // Глобальный флаг telegram уведомлений
}
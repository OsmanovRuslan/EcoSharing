package ru.ecosharing.user_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * DTO для представления настроек пользователя.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSettingsResponse {
    private UUID userId;
    private boolean enableEmailNotifications;
    private boolean enableTelegramNotifications;
    private String language;
    private String timezone;
    // Добавь сюда поля для настроек приватности, если они хранятся здесь
}
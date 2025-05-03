package ru.ecosharing.user_service.dto.request;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO для обновления настроек текущим пользователем.
 * Поля Boolean позволяют передавать null (оставить без изменений).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserSettingsRequest {
    private Boolean enableEmailNotifications;
    private Boolean enableTelegramNotifications;

    @Size(min = 2, max = 5, message = "Код языка должен быть 2-5 символов")
    private String language;

    @Size(max = 50, message = "Часовой пояс должен быть до 50 символов")
    private String timezone;
}
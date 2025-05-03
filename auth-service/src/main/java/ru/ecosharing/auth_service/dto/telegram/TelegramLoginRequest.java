package ru.ecosharing.auth_service.dto.telegram;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO для запроса входа существующего пользователя по логину/паролю
 * с одновременной привязкой его Telegram ID.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TelegramLoginRequest {
    /**
     * Telegram ID пользователя, который нужно привязать.
     */
    @NotBlank(message = "Telegram ID не может быть пустым")
    private String telegramId;

    /**
     * Имя пользователя (логин) для входа.
     */
    @NotBlank(message = "Имя пользователя (логин) не может быть пустым")
    private String username;

    /**
     * Пароль для входа.
     */
    @NotBlank(message = "Пароль не может быть пустым")
    private String password;
}
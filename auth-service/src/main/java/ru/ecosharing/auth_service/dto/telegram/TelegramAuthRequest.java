package ru.ecosharing.auth_service.dto.telegram;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO для запроса аутентификации через Telegram WebApp.
 * Содержит строку initData.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class TelegramAuthRequest {

    @NotBlank(message = "Данные инициализации Telegram (initData) не могут быть пустыми")
    private String initData;
}
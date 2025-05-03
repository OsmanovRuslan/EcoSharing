package ru.ecosharing.auth_service.dto.telegram;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.ecosharing.auth_service.dto.enumeration.TelegramAuthStatus;
import ru.ecosharing.auth_service.dto.response.JwtResponse;

/**
 * DTO для результата аутентификации через Telegram WebApp.
 * Содержит статус и, в зависимости от статуса, либо JWT токены, либо данные Telegram.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class TelegramAuthResult {

    private TelegramAuthStatus status; // Статус аутентификации

    private JwtResponse jwtResponse; // JWT токены (при status == SUCCESS)

    private TelegramUserData telegramUserData; // Данные из Telegram (при status == AUTH_REQUIRED)
}
package ru.ecosharing.auth_service.dto.enums;

/**
 * Перечисление возможных статусов результата аутентификации через Telegram.
 */
public enum TelegramAuthStatus {
    /**
     * Успешная аутентификация, пользователь найден и активен.
     * В результате будет присутствовать JwtResponse.
     */
    SUCCESS,

    /**
     * Учетные данные пользователя с таким Telegram ID не найдены в Auth Service.
     * Требуется дополнительное действие: вход по логину/паролю или регистрация.
     * В результате будет присутствовать TelegramUserData.
     */
    AUTH_REQUIRED
}
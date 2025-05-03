package ru.ecosharing.auth_service.service;

import ru.ecosharing.auth_service.dto.telegram.TelegramAuthRequest;
import ru.ecosharing.auth_service.dto.telegram.TelegramLoginRequest;
import ru.ecosharing.auth_service.dto.telegram.TelegramRegisterRequest;
import ru.ecosharing.auth_service.dto.response.JwtResponse;
import ru.ecosharing.auth_service.dto.telegram.TelegramAuthResult;

/**
 * Интерфейс сервиса для аутентификации и регистрации через Telegram WebApp.
 */
public interface TelegramAuthService {

    /**
     * Аутентифицирует пользователя на основе данных из Telegram WebApp initData.
     * Проверяет подпись, время жизни данных и наличие пользователя в системе.
     * @param request DTO с initData.
     * @return Результат аутентификации: либо JWT токены (если пользователь найден),
     *         либо статус AUTH_REQUIRED с данными из Telegram для формы входа/регистрации.
     */
    TelegramAuthResult authenticateTelegramUser(TelegramAuthRequest request);

    /**
     * Выполняет вход существующего пользователя по логину/паролю и привязывает его Telegram ID.
     * @param request DTO с логином, паролем и Telegram ID.
     * @return DTO с JWT токенами.
     */
    JwtResponse loginWithTelegram(TelegramLoginRequest request);

    /**
     * Регистрирует нового пользователя с использованием данных из Telegram и введенного пароля.
     * Привязывает Telegram ID. Взаимодействует с User Service для создания профиля.
     * @param request DTO с данными для регистрации (включая Telegram ID и пароль).
     * @return DTO с JWT токенами.
     */
    JwtResponse registerWithTelegram(TelegramRegisterRequest request);
}
package ru.ecosharing.auth_service.service;

import ru.ecosharing.auth_service.dto.request.LoginRequest;
import ru.ecosharing.auth_service.dto.request.RefreshTokenRequest;
import ru.ecosharing.auth_service.dto.request.RegisterRequest;
import ru.ecosharing.auth_service.dto.response.JwtResponse;

import java.util.UUID;

/**
 * Интерфейс сервиса для стандартной аутентификации и управления токенами.
 */
public interface AuthService {

    /**
     * Аутентифицирует пользователя по логину и паролю.
     * @param loginRequest DTO с логином и паролем.
     * @return DTO с JWT токенами и данными пользователя.
     */
    JwtResponse login(LoginRequest loginRequest);

    /**
     * Регистрирует нового пользователя.
     * Включает проверку секретных паролей для назначения ролей ADMIN/MODERATOR.
     * Взаимодействует с User Service для создания профиля.
     * @param registerRequest DTO с данными для регистрации.
     * @return DTO с JWT токенами и данными нового пользователя.
     */
    JwtResponse register(RegisterRequest registerRequest);

    /**
     * Обновляет пару Access и Refresh токенов, используя существующий Refresh токен.
     * @param refreshTokenRequest DTO с refresh токеном.
     * @return DTO с новыми JWT токенами.
     */
    JwtResponse refreshToken(RefreshTokenRequest refreshTokenRequest);

    /**
     * Выполняет выход пользователя из системы (инвалидирует refresh токен).
     * @param userId ID пользователя для выхода.
     */
    void logout(UUID userId);
}
package ru.ecosharing.auth_service.service;

import ru.ecosharing.auth_service.model.RefreshToken;

import java.util.Optional;
import java.util.UUID;

/**
 * Интерфейс сервиса для управления Refresh токенами.
 */
public interface RefreshTokenService {

    /**
     * Находит Refresh токен по его строковому значению.
     * @param token Строка токена.
     * @return Optional с найденным токеном.
     */
    Optional<RefreshToken> findByToken(String token);

    /**
     * Создает и сохраняет новый Refresh токен для пользователя.
     * Удаляет предыдущие токены этого пользователя.
     * @param userId ID пользователя.
     * @param username Имя пользователя (нужно для генерации токена).
     * @return Созданный RefreshToken.
     */
    RefreshToken createRefreshToken(UUID userId, String username);

    /**
     * Проверяет, не истек ли срок действия токена.
     * Если истек, удаляет токен из базы и выбрасывает исключение TokenRefreshException.
     * @param token Refresh токен для проверки.
     * @return Тот же токен, если он валиден.
     * @throws ru.ecosharing.auth_service.exception.TokenRefreshException если токен истек.
     */
    RefreshToken verifyExpiration(RefreshToken token);

    /**
     * Удаляет все Refresh токены для указанного пользователя.
     * @param userId ID пользователя.
     */
    void deleteByUserId(UUID userId);

    /**
     * Удаляет конкретный Refresh токен по его строковому значению.
     * @param token Строка токена для удаления.
     */
    void deleteToken(String token);
}
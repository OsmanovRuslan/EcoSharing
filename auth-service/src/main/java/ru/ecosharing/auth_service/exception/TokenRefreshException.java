package ru.ecosharing.auth_service.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Исключение, выбрасываемое при ошибках, связанных с обновлением JWT токена
 * (например, refresh токен не найден, истек или невалиден).
 * Соответствует HTTP статусу 403 Forbidden (т.к. пользователь не может получить новый токен).
 */
@ResponseStatus(HttpStatus.FORBIDDEN)
public class TokenRefreshException extends RuntimeException {
    public TokenRefreshException(String token, String message) {
        // Включаем часть токена (или его ID, если он есть) в сообщение для отладки
        super(String.format("Ошибка для токена [%.10s...]: %s", token, message));
    }

    public TokenRefreshException(String token, String message, Throwable cause) {
        super(String.format("Ошибка для токена [%.10s...]: %s", token, message), cause);
    }
}
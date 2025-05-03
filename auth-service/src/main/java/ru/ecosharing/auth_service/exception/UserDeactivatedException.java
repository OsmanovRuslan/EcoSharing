package ru.ecosharing.auth_service.exception;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Исключение, выбрасываемое при попытке входа или обновления токена
 * для деактивированного пользователя.
 * Соответствует HTTP статусу 403 Forbidden (или 401 Unauthorized, в зависимости от контекста).
 * Наследование от AuthenticationException позволяет Spring Security правильно его обработать.
 */
@ResponseStatus(HttpStatus.FORBIDDEN)
public class UserDeactivatedException extends AuthenticationException {
    public UserDeactivatedException(String message) {
        super(message);
    }
    public UserDeactivatedException(String message, Throwable cause) {
        super(message, cause);
    }
}
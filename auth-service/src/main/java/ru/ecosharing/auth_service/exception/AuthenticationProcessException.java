package ru.ecosharing.auth_service.exception;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Исключение для общих ошибок, возникающих в процессе аутентификации,
 * не связанных напрямую с неверными учетными данными (например, ошибки связи с другими сервисами).
 * Может соответствовать HTTP статусу 500 Internal Server Error или другому, в зависимости от причины.
 */
@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR) // По умолчанию 500, но может быть переопределено
public class AuthenticationProcessException extends AuthenticationException {
    public AuthenticationProcessException(String message) {
        super(message);
    }

    public AuthenticationProcessException(String message, Throwable cause) {
        super(message, cause);
    }
}
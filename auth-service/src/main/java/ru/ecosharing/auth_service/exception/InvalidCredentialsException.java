package ru.ecosharing.auth_service.exception;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException; // Можно наследовать отсюда для большей семантики
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Исключение, выбрасываемое при неверном логине или пароле во время аутентификации.
 * Соответствует HTTP статусу 401 Unauthorized.
 */
@ResponseStatus(HttpStatus.UNAUTHORIZED)
public class InvalidCredentialsException extends AuthenticationException { // Наследование от AuthenticationException
    public InvalidCredentialsException(String message) {
        super(message);
    }

    public InvalidCredentialsException(String message, Throwable cause) {
        super(message, cause);
    }
}
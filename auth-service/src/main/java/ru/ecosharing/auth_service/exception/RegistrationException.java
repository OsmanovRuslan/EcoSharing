package ru.ecosharing.auth_service.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Исключение, выбрасываемое при общих ошибках в процессе регистрации пользователя.
 * Обычно соответствует HTTP статусу 400 Bad Request.
 */
@ResponseStatus(HttpStatus.BAD_REQUEST) // Указывает возвращаемый HTTP статус по умолчанию
public class RegistrationException extends RuntimeException {
    public RegistrationException(String message) {
        super(message);
    }

    public RegistrationException(String message, Throwable cause) {
        super(message, cause);
    }
}
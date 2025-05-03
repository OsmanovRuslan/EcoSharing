package ru.ecosharing.auth_service.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Исключение, выбрасываемое при получении некорректных или невалидных данных
 * из Telegram WebApp initData (неверный формат, подпись, устаревшие данные).
 * Соответствует HTTP статусу 400 Bad Request.
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidTelegramDataException extends RuntimeException {
    public InvalidTelegramDataException(String message) {
        super(message);
    }
    public InvalidTelegramDataException(String message, Throwable cause) {
        super(message, cause);
    }
}
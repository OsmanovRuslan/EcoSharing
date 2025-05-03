package ru.ecosharing.notification_service.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Исключение для случаев, когда запрашиваемый ресурс не найден.
 * Например, уведомление по ID, пользователь по ID.
 * Соответствует HTTP статусу 404 Not Found.
 */
@ResponseStatus(HttpStatus.NOT_FOUND) // Устанавливаем статус 404
public class ResourceNotFoundException extends RuntimeException {

    /**
     * Конструктор с сообщением об ошибке.
     * @param message Сообщение, указывающее, какой ресурс не найден.
     */
    public ResourceNotFoundException(String message) {
        super(message);
    }

    /**
     * Конструктор с сообщением и причиной.
     * @param message Сообщение об ошибке.
     * @param cause Исходное исключение.
     */
    public ResourceNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
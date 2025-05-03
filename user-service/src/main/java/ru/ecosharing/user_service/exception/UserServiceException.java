package ru.ecosharing.user_service.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Общее исключение для ошибок бизнес-логики в User Service.
 * Может использоваться для различных ситуаций, где не подходит более специфичное исключение.
 * По умолчанию возвращает статус 500 Internal Server Error, но может быть переопределено
 * в GlobalExceptionHandler или через аннотацию @ResponseStatus в конкретных случаях.
 */
@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR) // Статус по умолчанию
public class UserServiceException extends RuntimeException {
    public UserServiceException(String message) {
        super(message);
    }

    public UserServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
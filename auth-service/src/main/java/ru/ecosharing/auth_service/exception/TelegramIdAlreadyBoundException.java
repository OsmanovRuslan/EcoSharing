package ru.ecosharing.auth_service.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Исключение, выбрасываемое при попытке привязать Telegram ID,
 * который уже используется другим пользователем, или при регистрации с уже существующим Telegram ID.
 * Соответствует HTTP статусу 409 Conflict.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class TelegramIdAlreadyBoundException extends RuntimeException {
    public TelegramIdAlreadyBoundException(String message) {
        super(message);
    }
}
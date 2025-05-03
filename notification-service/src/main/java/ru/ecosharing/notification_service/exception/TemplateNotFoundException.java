package ru.ecosharing.notification_service.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Исключение, выбрасываемое, когда шаблон уведомления (текст или тема)
 * не найден для запрошенной комбинации типа, канала и языка.
 * Указывает на возможную ошибку конфигурации шаблонов.
 * По умолчанию возвращает статус 500 Internal Server Error, так как
 * это проблема на стороне сервера.
 */
@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
public class TemplateNotFoundException extends RuntimeException {

    /**
     * Конструктор с сообщением об ошибке.
     * @param message Сообщение, описывающее, какой шаблон не найден.
     */
    public TemplateNotFoundException(String message) {
        super(message);
    }

    /**
     * Конструктор с сообщением и причиной (другим исключением).
     * @param message Сообщение об ошибке.
     * @param cause Исходное исключение (например, MissingResourceException).
     */
    public TemplateNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
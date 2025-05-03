package ru.ecosharing.notification_service.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
import ru.ecosharing.notification_service.model.enums.NotificationChannel; // Можно добавить канал

/**
 * Исключение, выбрасываемое при возникновении ошибки во время отправки
 * уведомления через конкретный канал доставки (Email, Telegram и т.д.).
 * Указывает на проблемы со связью с внешними системами или внутренние ошибки отправителя.
 * По умолчанию возвращает статус 500 Internal Server Error.
 */
@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR) // Проблема на стороне нашего сервиса или внешнего
public class NotificationSendException extends RuntimeException {

    private final NotificationChannel channel; // Опционально: канал, где произошла ошибка

    /**
     * Конструктор с сообщением.
     * @param message Сообщение об ошибке.
     */
    public NotificationSendException(String message) {
        super(message);
        this.channel = null;
    }

    /**
     * Конструктор с сообщением и причиной.
     * @param message Сообщение об ошибке.
     * @param cause Исходное исключение (например, MailSendException, FeignException, KafkaException).
     */
    public NotificationSendException(String message, Throwable cause) {
        super(message, cause);
        this.channel = null;
    }

    /**
     * Конструктор с сообщением, причиной и каналом.
     * @param message Сообщение об ошибке.
     * @param cause Исходное исключение.
     * @param channel Канал, на котором произошла ошибка.
     */
    public NotificationSendException(String message, Throwable cause, NotificationChannel channel) {
        super(message, cause);
        this.channel = channel;
    }

    /**
     * Возвращает канал, на котором произошла ошибка (если он был указан).
     * @return NotificationChannel или null.
     */
    public NotificationChannel getChannel() {
        return channel;
    }
}
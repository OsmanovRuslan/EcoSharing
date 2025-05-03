package ru.ecosharing.auth_service.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import ru.ecosharing.auth_service.dto.request.NotificationRequest; // DTO для запроса уведомления

/**
 * Feign клиент для взаимодействия с Telegram Bot Service (или Notification Service).
 * Имя 'telegram-bot-service' должно совпадать с именем сервиса-уведомителя.
 * URL можно указать явно: url = "${telegram-bot-service.url}"
 */
@FeignClient(name = "telegram-bot-service")
public interface NotificationClient {

    /**
     * Отправляет запрос на отправку уведомления.
     * @param notificationRequest DTO с данными уведомления.
     * @return ResponseEntity<Void> (успех или ошибка).
     */
    @PostMapping("/api/telegram/notify")
    ResponseEntity<Void> sendNotification(@RequestBody NotificationRequest notificationRequest);
}
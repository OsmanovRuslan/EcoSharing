package ru.ecosharing.notification_service.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import ru.ecosharing.notification_service.dto.UserTelegramIdDto; // DTO ответа Auth Service

import java.util.UUID;

/**
 * Feign клиент для взаимодействия с Auth Service.
 * Используется для получения Telegram ID пользователя, так как User Service его не предоставляет.
 * Требует наличия соответствующего эндпоинта в Auth Service.
 */
@FeignClient(name = "auth-service")
public interface AuthServiceClient {

    /**
     * Получает Telegram ID пользователя по его системному ID.
     * Обращается к внутреннему API Auth Service.
     *
     * @param userId Системный ID пользователя.
     * @return ResponseEntity с UserTelegramIdDto (поле telegramId может быть null).
     *         Ожидается статус 200 OK при успехе, 404 Not Found или другие ошибки.
     */
    @GetMapping("/api/internal/users/{userId}/telegram-id")
    ResponseEntity<UserTelegramIdDto> getTelegramIdByUserId(@PathVariable("userId") UUID userId);

}
package ru.ecosharing.notification_service.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import ru.ecosharing.notification_service.dto.UserNotificationDetailsDto;

import java.util.UUID;

/**
 * Feign клиент для взаимодействия с User Service.
 * Имя 'user-service' должно совпадать с `spring.application.name` сервиса пользователей
 * и быть зарегистрировано в Eureka (или указан явный URL).
 */
@FeignClient(name = "user-service")
public interface UserServiceClient {

    /**
     * Получает детали пользователя, необходимые для отправки уведомлений
     * (email, язык, настройки каналов).
     * Обращается к внутреннему API User Service.
     *
     * @param userId ID пользователя.
     * @return ResponseEntity с UserNotificationDetailsDto. Ожидается статус 200 OK при успехе,
     *         404 Not Found, если пользователь не найден, или другие ошибки (5xx).
     */
    @GetMapping("/api/internal/users/{userId}/notification-details")
    ResponseEntity<UserNotificationDetailsDto> getUserNotificationDetails(@PathVariable("userId") UUID userId);

}
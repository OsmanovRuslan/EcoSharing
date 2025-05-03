package ru.ecosharing.telegram_bot_service.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Клиент для взаимодействия с сервисом пользователей
 */
@FeignClient(name = "user-service")
public interface UserServiceClient {

    /**
     * Проверяет, существует ли пользователь с указанным telegramId и разрешил ли он уведомления
     *
     * @param telegramId ID пользователя в Telegram
     * @return true, если пользователь существует и разрешил уведомления
     */
    @GetMapping("/api/users/telegram/{telegramId}/notifications-enabled")
    boolean isUserExistsAndNotificationsEnabled(@PathVariable String telegramId);

    /**
     * Получает ID пользователя в Telegram по его ID в системе
     *
     * @param userId ID пользователя в системе
     * @return ID пользователя в Telegram или null, если не найден
     */
    @GetMapping("/api/users/{userId}/telegram-id")
    String getTelegramIdByUserId(@PathVariable String userId);
}
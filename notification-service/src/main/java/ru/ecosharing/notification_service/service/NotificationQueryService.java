package ru.ecosharing.notification_service.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import ru.ecosharing.notification_service.dto.UserNotificationDto;
import ru.ecosharing.notification_service.exception.ResourceNotFoundException;

import java.util.Collection;
import java.util.UUID;

/**
 * Интерфейс сервиса для операций чтения и управления статусом прочитанности уведомлений.
 * Предоставляет методы для API фронтенда.
 */
public interface NotificationQueryService {

    /**
     * Получает страницу уведомлений для указанного пользователя.
     * Позволяет фильтровать по статусу прочтения и использовать пагинацию/сортировку.
     *
     * @param userId ID пользователя.
     * @param unread Фильтр по непрочитанным (true), прочитанным (false) или всем (null).
     * @param pageable Параметры пагинации и сортировки.
     * @return Страница (Page) с DTO уведомлений.
     */
    Page<UserNotificationDto> getUserNotifications(UUID userId, Boolean unread, Pageable pageable);

    /**
     * Получает количество непрочитанных уведомлений для указанного пользователя.
     *
     * @param userId ID пользователя.
     * @return Количество непрочитанных уведомлений.
     */
    long getUnreadNotificationCount(UUID userId);

    /**
     * Помечает указанные уведомления как прочитанные для данного пользователя.
     *
     * @param userId ID пользователя, которому принадлежат уведомления.
     * @param notificationIds Коллекция ID уведомлений для пометки.
     * @throws ResourceNotFoundException если какое-либо уведомление не найдено или не принадлежит пользователю (зависит от реализации).
     */
    void markNotificationsAsRead(UUID userId, Collection<UUID> notificationIds);

    /**
     * Помечает все непрочитанные уведомления пользователя как прочитанные.
     *
     * @param userId ID пользователя.
     * @return Количество уведомлений, которые были помечены как прочитанные.
     */
    long markAllNotificationsAsRead(UUID userId);
}
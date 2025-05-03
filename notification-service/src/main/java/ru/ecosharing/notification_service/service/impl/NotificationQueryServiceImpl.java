package ru.ecosharing.notification_service.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.ecosharing.notification_service.dto.UserNotificationDto;
import ru.ecosharing.notification_service.model.UserNotification;
import ru.ecosharing.notification_service.mapper.NotificationMapper;
import ru.ecosharing.notification_service.repository.UserNotificationRepository;
import ru.ecosharing.notification_service.service.NotificationQueryService;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.UUID;

/**
 * Реализация сервиса для операций чтения и управления статусом прочитанности уведомлений.
 * Предоставляет методы для API.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationQueryServiceImpl implements NotificationQueryService {

    private final UserNotificationRepository notificationRepository;

    /**
     * Получает страницу уведомлений пользователя с фильтрацией и пагинацией.
     */
    @Override
    @Transactional(readOnly = true) // Операция чтения
    public Page<UserNotificationDto> getUserNotifications(UUID userId, Boolean unread, Pageable pageable) {
        log.debug("Запрос страницы уведомлений для userId={}, unread={}, pageable={}", userId, unread, pageable);

        Page<UserNotification> notificationPage;
        if (unread == null) {
            // Запрашиваем все уведомления пользователя
            notificationPage = notificationRepository.findByUserId(userId, pageable);
        } else {
            notificationPage = notificationRepository.findByUserIdAndIsReadIs(userId, !unread, pageable); // Инвертируем, так как isRead
        }

        Page<UserNotificationDto> dtoPage = notificationPage.map(NotificationMapper::toDto);
        log.debug("Найдено {} уведомлений на странице {} для пользователя {}", dtoPage.getNumberOfElements(), pageable.getPageNumber(), userId);
        return dtoPage;
    }

    /**
     * Получает количество непрочитанных уведомлений пользователя.
     */
    @Override
    @Transactional(readOnly = true)
    public long getUnreadNotificationCount(UUID userId) {
        log.debug("Запрос количества непрочитанных уведомлений для userId={}", userId);
        long count = notificationRepository.countByUserIdAndIsReadFalse(userId);
        log.debug("Количество непрочитанных уведомлений для userId={}: {}", userId, count);
        return count;
    }

    /**
     * Помечает указанные уведомления как прочитанные.
     */
    @Override
    @Transactional
    public void markNotificationsAsRead(UUID userId, Collection<UUID> notificationIds) {
        // Проверка на пустой список ID
        if (notificationIds == null || notificationIds.isEmpty()) {
            log.warn("Получен пустой или null список ID для пометки прочитанными для userId={}", userId);
            return; // Ничего не делаем
        }
        log.info("Пометка уведомлений {} как прочитанных для пользователя {}", notificationIds, userId);
        int updatedCount = notificationRepository.markAsRead(userId, notificationIds, LocalDateTime.now());
        log.info("Помечено {} уведомлений как прочитанные для пользователя {}", updatedCount, userId);
        // Здесь можно было бы отправить событие через SSE об изменении статуса, если фронтенд это требует
        // sseService.sendStatusUpdate(userId, notificationIds);
    }

    /**
     * Помечает все непрочитанные уведомления пользователя как прочитанные.
     */
    @Override
    @Transactional // Операция записи, нужна транзакция
    public long markAllNotificationsAsRead(UUID userId) {
        log.info("Пометка ВСЕХ непрочитанных уведомлений как прочитанных для пользователя {}", userId);
        int updatedCount = notificationRepository.markAllAsRead(userId, LocalDateTime.now());
        log.info("Помечено {} уведомлений как прочитанные для пользователя {}", updatedCount, userId);
        // Здесь можно было бы отправить событие через SSE об изменении статуса
        // sseService.sendAllReadConfirmation(userId);
        return updatedCount;
    }
}
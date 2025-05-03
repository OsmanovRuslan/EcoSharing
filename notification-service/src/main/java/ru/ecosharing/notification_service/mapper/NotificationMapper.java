package ru.ecosharing.notification_service.mapper;

import ru.ecosharing.notification_service.dto.UserNotificationDto;
import ru.ecosharing.notification_service.model.UserNotification;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Утилитарный класс-маппер для преобразования между сущностью уведомления
 * (UserNotification) и ее представлением для клиента (UserNotificationDto).
 * Использует статические методы, так как логика маппинга проста.
 */
public final class NotificationMapper { // Объявляем класс финальным

    private NotificationMapper() {
        throw new UnsupportedOperationException("Это утилитарный класс, его не нужно инстанцировать");
    }

    /**
     * Преобразует сущность UserNotification в UserNotificationDto.
     *
     * @param entity Сущность уведомления из базы данных.
     * @return DTO уведомления или null, если entity равно null.
     */
    public static UserNotificationDto toDto(UserNotification entity) {
        if (entity == null) {
            return null;
        }

        // Используем Builder для создания DTO
        return UserNotificationDto.builder()
                .id(entity.getId())
                .notificationType(entity.getNotificationType())
                .message(entity.getMessage())
                .targetUrl(entity.getTargetUrl())
                .isRead(entity.isRead())
                .createdAt(entity.getCreatedAt())
                .readAt(entity.getReadAt())
                .build();
    }

    /**
     * Преобразует список сущностей UserNotification в список UserNotificationDto.
     *
     * @param entities Список сущностей уведомлений.
     * @return Список DTO уведомлений или пустой список, если entities равно null.
     */
    public static List<UserNotificationDto> toDtoList(List<UserNotification> entities) {
        if (entities == null) {
            return Collections.emptyList();
        }

        return entities.stream()
                .map(NotificationMapper::toDto)
                .collect(Collectors.toList());
    }

}
package ru.ecosharing.notification_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.ecosharing.notification_service.model.enums.NotificationType;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserNotificationDto {

    private UUID id;

    private NotificationType notificationType;

    private String message;

    private String targetUrl;

    private boolean isRead;

    private LocalDateTime createdAt;

    private LocalDateTime readAt;

}
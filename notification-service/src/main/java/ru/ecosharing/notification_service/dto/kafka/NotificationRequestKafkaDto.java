package ru.ecosharing.notification_service.dto.kafka;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.ecosharing.notification_service.model.enums.NotificationType;

import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationRequestKafkaDto {

    private UUID userId;

    private String recipientTelegramId;

    private NotificationType notificationType;

    private Map<String, String> params;

    private String targetUrl;

    @Builder.Default
    private boolean attachWebAppButton = false;
}
package ru.ecosharing.notification_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserNotificationDetailsDto {

    private UUID userId;

    private String email;

    private String firstName;

    private String language;

    private boolean emailNotificationsEnabled;

    private boolean telegramNotificationsEnabled;

}
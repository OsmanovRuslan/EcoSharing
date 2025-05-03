package ru.ecosharing.notification_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Внутренний DTO для передачи основной информации о получателе
 * в конкретные реализации Notifier.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileDto {

    private UUID userId;

    private String email;

    private String telegramId;

    private String firstName;

}
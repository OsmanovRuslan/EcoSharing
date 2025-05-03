package ru.ecosharing.notification_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserTelegramIdDto {

    private UUID userId;

    private String telegramId;

}
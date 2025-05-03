package ru.ecosharing.notification_service.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.NotEmpty; // Для валидации списка
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
public class MarkReadRequestDto {

    @NotEmpty(message = "Список ID уведомлений не должен быть пустым")
    @NotNull(message = "Список ID уведомлений не может быть null")
    private List<UUID> notificationIds;

}
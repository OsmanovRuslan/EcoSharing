package ru.ecosharing.listing_service.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import ru.ecosharing.listing_service.enums.ModerationStatus;

@Data
public class ModerateListingRequest {

    @NotNull(message = "Новый статус модерации не может быть null")
    private ModerationStatus newStatus; // ACTIVE, NEEDS_REVISION, REJECTED

    @Size(max = 2000, message = "Комментарий модератора слишком длинный")
    private String moderationComment; // Обязателен для NEEDS_REVISION и REJECTED

    @Size(max = 500, message = "Причина отклонения слишком длинная")
    private String rejectionReason; // Опционально, для детализации
}
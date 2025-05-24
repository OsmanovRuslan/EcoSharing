package ru.ecosharing.listing_service.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;
import java.util.UUID;

@Data
public class UpdateCategoryRequest {

    @Size(min = 2, max = 100)
    private String name;

    private UUID parentId; // Позволяет изменить родителя, null - сделать корневой

    @Size(max = 1000)
    private String description;

    private Boolean isActive; // Для активации/деактивации
}
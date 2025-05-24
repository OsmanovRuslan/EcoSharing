package ru.ecosharing.listing_service.dto.response;

import lombok.Builder;
import lombok.Data;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class CategoryResponse {
    private UUID id;
    private String name;
    private String description;
    private UUID parentId;
    private List<CategoryResponse> children; // Для отображения иерархии (может быть null или пустым)
    private boolean isActive; // Добавлено, чтобы клиент знал об активности
}
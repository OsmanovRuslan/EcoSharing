package ru.ecosharing.listing_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.util.UUID;

@Data
public class CreateCategoryRequest {

    @NotBlank(message = "Название категории не может быть пустым")
    @Size(min = 2, max = 100, message = "Длина названия категории от 2 до 100 символов")
    private String name;

    private UUID parentId; // Может быть null для корневой категории

    @Size(max = 1000, message = "Описание категории слишком длинное")
    private String description;

}
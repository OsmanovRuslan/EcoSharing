package ru.ecosharing.listing_service.mapper;

import org.mapstruct.Named;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import ru.ecosharing.listing_service.model.Category;
import ru.ecosharing.listing_service.repository.CategoryRepository;
import ru.ecosharing.listing_service.exception.ResourceNotFoundException;

import java.util.UUID;

@Component
public class ListingMapperHelper {

    private final CategoryRepository categoryRepository;

    @Autowired
    public ListingMapperHelper(@Lazy CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Named("uuidToCategory")
    public Category uuidToCategory(UUID categoryId) {
        if (categoryId == null) {
            throw new IllegalArgumentException("categoryId не может быть null при создании Listing.");
        }
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Категория с ID " + categoryId + " не найдена."));
    }

    @Named("uuidToCategoryNullable")
    public Category uuidToCategoryNullable(UUID categoryId) {
        if (categoryId == null) {
            return null; // Если в DTO для обновления categoryId не передали, возвращаем null (не меняем категорию)
        }
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Категория с ID " + categoryId + " не найдена при обновлении."));
    }
}
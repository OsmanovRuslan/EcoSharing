package ru.ecosharing.listing_service.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import ru.ecosharing.listing_service.dto.request.CreateCategoryRequest;
import ru.ecosharing.listing_service.dto.request.UpdateCategoryRequest;
import ru.ecosharing.listing_service.dto.response.CategoryResponse;
import ru.ecosharing.listing_service.model.Category; // Для внутреннего использования

import java.util.List;
import java.util.UUID;

public interface CategoryService {

    /**
     * Создает новую категорию. (Доступно администратору)
     * @param request DTO с данными для создания категории.
     * @return DTO созданной категории.
     */
    CategoryResponse createCategory(CreateCategoryRequest request);

    /**
     * Обновляет существующую категорию. (Доступно администратору)
     * @param categoryId ID категории для обновления.
     * @param request DTO с данными для обновления.
     * @return DTO обновленной категории.
     */
    CategoryResponse updateCategory(UUID categoryId, UpdateCategoryRequest request);

    /**
     * Удаляет категорию. (Доступно администратору)
     * Логика должна учитывать наличие объявлений в этой категории.
     * @param categoryId ID категории для удаления.
     */
    void deleteCategory(UUID categoryId);

    /**
     * Получает категорию по ID.
     * @param categoryId ID категории.
     * @return DTO категории.
     */
    CategoryResponse getCategoryById(UUID categoryId);

    /**
     * Получает список всех активных категорий (может быть с иерархией).
     * Используется для отображения пользователям.
     * @return Список DTO категорий.
     */
    List<CategoryResponse> getAllActiveCategories();

    /**
     * Получает список всех категорий (включая неактивные) для административных целей.
     * @param pageable Параметры пагинации.
     * @return Страница с DTO категорий.
     */
    Page<CategoryResponse> getAllCategoriesAdmin(Pageable pageable);


    // Внутренний метод, не для API, для использования другими сервисами, если нужна сама сущность
    Category findCategoryEntityById(UUID categoryId);
}
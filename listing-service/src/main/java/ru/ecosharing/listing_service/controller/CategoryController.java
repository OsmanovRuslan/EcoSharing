package ru.ecosharing.listing_service.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import ru.ecosharing.listing_service.dto.request.CreateCategoryRequest;
import ru.ecosharing.listing_service.dto.request.UpdateCategoryRequest;
import ru.ecosharing.listing_service.dto.response.CategoryResponse;
import ru.ecosharing.listing_service.dto.response.MessageResponse;
import ru.ecosharing.listing_service.service.CategoryService;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    // --- Публичные эндпоинты ---
    @GetMapping
    public ResponseEntity<List<CategoryResponse>> getAllActiveCategories() {
        log.info("GET /api/categories - Fetching all active categories");
        List<CategoryResponse> categories = categoryService.getAllActiveCategories();
        return ResponseEntity.ok(categories);
    }

    @GetMapping("/{categoryId}")
    public ResponseEntity<CategoryResponse> getCategoryById(@PathVariable UUID categoryId) {
        log.info("GET /api/categories/{} - Fetching category by ID", categoryId);
        CategoryResponse category = categoryService.getCategoryById(categoryId);
        // Предполагаем, что сервис бросит ResourceNotFoundException, если не найдено,
        // и GlobalExceptionHandler вернет 404.
        return ResponseEntity.ok(category);
    }

    // --- Эндпоинты для администраторов ---
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CategoryResponse> createCategory(@Valid @RequestBody CreateCategoryRequest request) {
        log.info("POST /api/categories - Admin creating category: {}", request.getName());
        CategoryResponse createdCategory = categoryService.createCategory(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdCategory);
    }

    @PutMapping("/{categoryId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CategoryResponse> updateCategory(@PathVariable UUID categoryId,
                                                           @Valid @RequestBody UpdateCategoryRequest request) {
        log.info("PUT /api/categories/{} - Admin updating category", categoryId);
        CategoryResponse updatedCategory = categoryService.updateCategory(categoryId, request);
        return ResponseEntity.ok(updatedCategory);
    }

    @DeleteMapping("/{categoryId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MessageResponse> deleteCategory(@PathVariable UUID categoryId) {
        log.warn("DELETE /api/categories/{} - Admin attempting to delete category", categoryId);
        categoryService.deleteCategory(categoryId);
        return ResponseEntity.ok(new MessageResponse("Категория с ID " + categoryId + " успешно удалена."));
    }

    @GetMapping("/admin/all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<CategoryResponse>> getAllCategoriesAdmin(
            @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        log.info("GET /api/categories/admin/all - Admin fetching all categories with pagination: {}", pageable);
        Page<CategoryResponse> categoriesPage = categoryService.getAllCategoriesAdmin(pageable);
        return ResponseEntity.ok(categoriesPage);
    }
}
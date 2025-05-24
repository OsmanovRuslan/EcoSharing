package ru.ecosharing.listing_service.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.ecosharing.listing_service.dto.request.CreateCategoryRequest;
import ru.ecosharing.listing_service.dto.request.UpdateCategoryRequest;
import ru.ecosharing.listing_service.dto.response.CategoryResponse;
import ru.ecosharing.listing_service.exception.CategoryOperationException;
import ru.ecosharing.listing_service.exception.ResourceNotFoundException;
import ru.ecosharing.listing_service.dto.kafka.CategoryLifecycleEvent;
import ru.ecosharing.listing_service.kafka.producer.ListingEventProducer; // Предполагаем, что он шлет и события категорий
import ru.ecosharing.listing_service.mapper.CategoryMapper;
import ru.ecosharing.listing_service.model.Category;
import ru.ecosharing.listing_service.model.Listing; // Нужен для проверки при деактивации
import ru.ecosharing.listing_service.enums.ModerationStatus; // Нужен для логики деактивации
import ru.ecosharing.listing_service.repository.CategoryRepository;
import ru.ecosharing.listing_service.repository.ListingRepository; // Нужен для обновления объявлений
import ru.ecosharing.listing_service.service.CategoryService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;
    private final CategoryMapper categoryMapper;
    private final ListingRepository listingRepository;
    private final ListingEventProducer eventProducer; // Для отправки событий Kafka

    // private final NotificationServiceInternalClient notificationClient; // Альтернатива Kafka

    @Override
    @Transactional
    public CategoryResponse createCategory(CreateCategoryRequest request) {
        log.info("Creating new category with name: {}", request.getName());
        if (categoryRepository.findByName(request.getName()).isPresent()) {
            throw new CategoryOperationException("Категория с названием '" + request.getName() + "' уже существует.");
        }

        Category category = categoryMapper.toCategory(request);
        if (request.getParentId() != null) {
            Category parent = findCategoryEntityByIdInternal(request.getParentId());
            category.setParent(parent);
        }

        Category savedCategory = categoryRepository.save(category);
        log.info("Category created with ID: {}", savedCategory.getId());
        // Отправка события о создании категории (если нужно)
        // eventProducer.sendCategoryLifecycleEvent(new CategoryLifecycleEvent(savedCategory.getId(), "CATEGORY_CREATED", savedCategory.isActive()));
        return categoryMapper.toCategoryResponse(savedCategory);
    }

    @Override
    @Transactional
    public CategoryResponse updateCategory(UUID categoryId, UpdateCategoryRequest request) {
        log.info("Updating category with ID: {}", categoryId);
        Category category = findCategoryEntityByIdInternal(categoryId);

        // Проверка на уникальность имени, если оно меняется
        if (request.getName() != null && !request.getName().equals(category.getName())) {
            categoryRepository.findByName(request.getName()).ifPresent(existing -> {
                if (!existing.getId().equals(categoryId)) {
                    throw new CategoryOperationException("Категория с названием '" + request.getName() + "' уже существует.");
                }
            });
        }

        // Логика перед изменением isActive
        Boolean requestedIsActive = request.getIsActive();
        boolean wasActive = category.isActive();

        // Обновляем поля с помощью MapStruct (он учтет NullValuePropertyMappingStrategy.IGNORE)
        categoryMapper.updateCategoryFromDto(request, category);

        // Если parentId передан в DTO, обновляем родителя
        if (request.getParentId() != null) {
            if (request.getParentId().equals(categoryId)) { // Проверка на установку себя в качестве родителя
                throw new CategoryOperationException("Категория не может быть родителем для самой себя.");
            }
            Category parent = findCategoryEntityByIdInternal(request.getParentId());
            category.setParent(parent);
        } else if (request.getParentId() == null && request.getName() != null){ // Если parentId явно передан как null (то есть был запрос на удаление родителя)
            // но это условие нужно уточнить, в UpdateCategoryRequest parentId не обязателен,
            // если он null в DTO, это значит "не менять родителя" из-за IGNORE стратегии.
            // Если нужно явно сделать категорию корневой, нужен другой механизм или флаг в DTO.
            // Пока считаем, что null в DTO = не менять. Если нужно сделать корневой, это отдельная логика.
        }


        Category updatedCategory = categoryRepository.save(category);

        // Логика после изменения isActive
        if (requestedIsActive != null && wasActive && !requestedIsActive) { // Категория была активна и стала неактивна
            handleCategoryDeactivation(updatedCategory);
            eventProducer.sendCategoryLifecycleEvent(new CategoryLifecycleEvent(updatedCategory.getId(), "CATEGORY_DEACTIVATED", false));
        } else if (requestedIsActive != null && !wasActive && requestedIsActive) { // Категория была неактивна и стала активна
            log.info("Category {} activated.", updatedCategory.getId());
            eventProducer.sendCategoryLifecycleEvent(new CategoryLifecycleEvent(updatedCategory.getId(), "CATEGORY_ACTIVATED", true));
        } else {
            // Если статус активности не менялся или менялся с сохранением текущего значения,
            // или если менялись другие поля, кроме isActive
            eventProducer.sendCategoryLifecycleEvent(new CategoryLifecycleEvent(updatedCategory.getId(), "CATEGORY_UPDATED", updatedCategory.isActive()));
        }

        log.info("Category updated with ID: {}", updatedCategory.getId());
        return categoryMapper.toCategoryResponse(updatedCategory);
    }

    private void handleCategoryDeactivation(Category deactivatedCategory) {
        log.warn("Category {} is being deactivated. Processing associated listings.", deactivatedCategory.getId());
        List<Listing> listingsToUpdate = listingRepository.findAllByCategoryAndModerationStatus(
                deactivatedCategory, ModerationStatus.ACTIVE
        );

        if (!listingsToUpdate.isEmpty()) {
            String moderationComment = "Категория '" + deactivatedCategory.getName() +
                    "', в которой размещено ваше объявление, была деактивирована. " +
                    "Пожалуйста, выберите новую категорию и отправьте объявление на повторную модерацию.";

            for (Listing listing : listingsToUpdate) {
                listing.setModerationStatus(ModerationStatus.NEEDS_REVISION);
                listing.setModerationComment(moderationComment);
                listing.setLastModeratedAt(LocalDateTime.now()); // Обновляем время "модерации"
                listingRepository.save(listing); // Сохраняем изменения

                // Отправляем событие в Kafka для обновления ES и уведомления пользователю
                eventProducer.sendListingModerationStatusChangedEvent(
                        listing.getId(),
                        ModerationStatus.NEEDS_REVISION,
                        ModerationStatus.ACTIVE, // old status
                        null // moderatorId - системное изменение
                );

                // Здесь также нужно отправить уведомление пользователю через Notification Service
                // (например, через Kafka, если NotificationService слушает события ListingModerationStatusChangedEvent
                // или через прямое событие для NotificationService)
                // Пример:
                // NotificationRequestDto notification = NotificationRequestDto.builder()
                // .userId(listing.getUserId())
                // .notificationType(NotificationType.LISTING_CATEGORY_DEACTIVATED) // или LISTING_NEEDS_REVISION
                // .params(Map.of("listingTitle", listing.getTitle(), "categoryName", deactivatedCategory.getName(), "reason", moderationComment))
                // .targetUrl("/my-listings/" + listing.getId() + "/edit") // ссылка на редактирование
                // .build();
                // eventProducer.sendNotificationRequest(notification);
                log.info("Listing {} (User ID: {}) status set to NEEDS_REVISION due to category deactivation.", listing.getId(), listing.getUserId());
            }
            log.info("{} listings updated to NEEDS_REVISION due to deactivation of category '{}'", listingsToUpdate.size(), deactivatedCategory.getName());
        }
    }


    @Override
    @Transactional
    public void deleteCategory(UUID categoryId) {
        log.warn("Attempting to delete category with ID: {}", categoryId);
        Category category = findCategoryEntityByIdInternal(categoryId);

        if (listingRepository.existsByCategory(category)) {
            throw new CategoryOperationException("Невозможно удалить категорию '" + category.getName() + "', так как в ней есть объявления.");
        }

        if (categoryRepository.existsByParentIdAndIsActiveTrue(categoryId) || !category.getChildren().isEmpty()) {
            // Более строгая проверка, если дети есть, даже неактивные.
            // Либо разрешать удаление и делать детей корневыми (SET NULL уже настроен в FK)
            // Для простоты, пока запретим удаление, если есть дочерние.
            throw new CategoryOperationException("Невозможно удалить категорию '" + category.getName() + "', так как у нее есть дочерние категории.");
        }

        categoryRepository.delete(category);
        // Отправка события об удалении категории (если нужно)
        // eventProducer.sendCategoryLifecycleEvent(new CategoryLifecycleEvent(categoryId, "CATEGORY_DELETED", false));
        log.info("Category with ID: {} deleted successfully.", categoryId);
    }

    @Override
    @Transactional(readOnly = true)
    public CategoryResponse getCategoryById(UUID categoryId) {
        return categoryMapper.toCategoryResponse(findCategoryEntityByIdInternal(categoryId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategoryResponse> getAllActiveCategories() {
        // Здесь можно реализовать логику для построения дерева или плоского списка,
        // в зависимости от того, как CategoryMapper настроен для поля children.
        // Для примера, вернем корневые активные категории, а маппер раскроет их детей.
        List<Category> rootCategories = categoryRepository.findAllByParentIsNullAndIsActiveTrue();
        return categoryMapper.toCategoryResponseList(rootCategories);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<CategoryResponse> getAllCategoriesAdmin(Pageable pageable) {
        return categoryRepository.findAll(pageable).map(categoryMapper::toCategoryResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Category findCategoryEntityById(UUID categoryId) {
        return findCategoryEntityByIdInternal(categoryId);
    }

    private Category findCategoryEntityByIdInternal(UUID categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Категория с ID " + categoryId + " не найдена."));
    }
}
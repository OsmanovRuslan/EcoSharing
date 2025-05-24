package ru.ecosharing.listing_service.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification; // Для фильтрации
import ru.ecosharing.listing_service.dto.request.CreateListingRequest;
import ru.ecosharing.listing_service.dto.request.UpdateListingRequest;
import ru.ecosharing.listing_service.dto.response.ListingResponse;
import ru.ecosharing.listing_service.dto.response.ListingSummaryResponse;
import ru.ecosharing.listing_service.elasticsearch.document.ListingDocument; // Если спецификация на уровне ES-документа
import ru.ecosharing.listing_service.enums.AvailabilityStatus;
import ru.ecosharing.listing_service.model.Listing; // Если спецификация на уровне JPA-сущности

import java.math.BigDecimal;
import java.util.UUID;

public interface ListingService {

    // --- Пользовательские операции ---

    /**
     * Создает новое объявление для текущего аутентифицированного пользователя.
     * @param userId ID текущего пользователя.
     * @param request DTO с данными для создания объявления.
     * @return DTO созданного объявления.
     */
    ListingResponse createListing(UUID userId, CreateListingRequest request);

    /**
     * Обновляет существующее объявление, принадлежащее текущему пользователю.
     * @param userId ID текущего пользователя.
     * @param listingId ID объявления для обновления.
     * @param request DTO с данными для обновления.
     * @return DTO обновленного объявления.
     */
    ListingResponse updateMyListing(UUID userId, UUID listingId, UpdateListingRequest request);

    /**
     * Удаляет объявление, принадлежащее текущему пользователю.
     * @param userId ID текущего пользователя.
     * @param listingId ID объявления для удаления.
     */
    void deleteMyListing(UUID userId, UUID listingId);

    /**
     * Получает объявление по ID, если оно принадлежит текущему пользователю или является публичным.
     * Используется для детального просмотра. Увеличивает счетчик просмотров.
     * @param listingId ID объявления.
     * @param currentUserId ID текущего пользователя (может быть null для анонимного просмотра публичных).
     * @return DTO объявления.
     */
    ListingResponse getListingById(UUID listingId, UUID currentUserId);

    /**
     * Получает список объявлений, принадлежащих текущему пользователю.
     * @param userId ID текущего пользователя.
     * @param pageable Параметры пагинации и сортировки.
     * @return Страница с DTO краткой информации об объявлениях.
     */
    Page<ListingSummaryResponse> getMyListings(UUID userId, Pageable pageable);

    /**
     * Активирует объявление пользователя (переводит из INACTIVE).
     * @param userId ID текущего пользователя.
     * @param listingId ID объявления.
     * @return DTO обновленного объявления.
     */
    ListingResponse activateMyListing(UUID userId, UUID listingId);

    /**
     * Деактивирует объявление пользователя (переводит в INACTIVE).
     * @param userId ID текущего пользователя.
     * @param listingId ID объявления.
     * @return DTO обновленного объявления.
     */
    ListingResponse deactivateMyListing(UUID userId, UUID listingId);


    // --- Публичные операции (поиск) ---

    /**
     * Поиск активных объявлений с фильтрацией, пагинацией и сортировкой.
     * Этот метод будет взаимодействовать с Elasticsearch.
     * @param pageable Параметры пагинации и сортировки.
     * @return Страница с DTO краткой информации об объявлениях.
     */
    Page<ListingSummaryResponse> searchListings(UUID categoryId,
                                                String searchTerm,
                                                String locationText,
                                                BigDecimal priceFrom,
                                                BigDecimal priceTo,
                                                AvailabilityStatus availabilityStatus, Pageable pageable);
    /**
     * Получает список объявлений определенного пользователя.
     * @param ownerUserId ID владельца объявлений.
     * @param pageable Параметры пагинации.
     * @return Страница с DTO краткой информации об объявлениях.
     */
    Page<ListingSummaryResponse>getListingsByOwner(UUID ownerUserId, Pageable pageable);


    // --- Операции с избранным ---

    /**
     * Добавляет объявление в избранное для текущего пользователя.
     * @param userId ID текущего пользователя.
     * @param listingId ID объявления.
     */
    void addListingToFavorites(UUID userId, UUID listingId);

    /**
     * Удаляет объявление из избранного для текущего пользователя.
     * @param userId ID текущего пользователя.
     * @param listingId ID объявления.
     */
    void removeListingFromFavorites(UUID userId, UUID listingId);

    /**
     * Получает список избранных объявлений текущего пользователя.
     * @param userId ID текущего пользователя.
     * @param pageable Параметры пагинации.
     * @return Страница с DTO краткой информации об избранных объявлениях.
     */
    Page<ListingSummaryResponse> getFavoriteListings(UUID userId, Pageable pageable);

}
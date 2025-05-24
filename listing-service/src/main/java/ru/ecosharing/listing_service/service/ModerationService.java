package ru.ecosharing.listing_service.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import ru.ecosharing.listing_service.dto.request.ModerateListingRequest;
import ru.ecosharing.listing_service.dto.response.ListingResponse; // Модератор видит полное объявление
import ru.ecosharing.listing_service.dto.response.ModerationListingResponse; // Или специальный DTO
import java.util.UUID;

public interface ModerationService {

    /**
     * Получает список объявлений, ожидающих модерации.
     * @param pageable Параметры пагинации.
     * @return Страница с DTO объявлений для модерации.
     */
    Page<ModerationListingResponse> getPendingModerationListings(Pageable pageable);

    /**
     * Одобряет объявление.
     * @param listingId ID объявления.
     * @param moderatorId ID модератора, выполняющего действие.
     * @return DTO одобренного объявления.
     */
    ListingResponse approveListing(UUID listingId, UUID moderatorId);

    /**
     * Отправляет объявление на доработку пользователю.
     * @param listingId ID объявления.
     * @param moderatorId ID модератора.
     * @param request DTO с комментарием и причиной.
     * @return DTO объявления со статусом NEEDS_REVISION.
     */
    ListingResponse sendListingForRevision(UUID listingId, UUID moderatorId, ModerateListingRequest request);

    /**
     * Окончательно отклоняет объявление.
     * @param listingId ID объявления.
     * @param moderatorId ID модератора.
     * @param request DTO с комментарием и причиной.
     * @return DTO объявления со статусом REJECTED.
     */
    ListingResponse rejectListing(UUID listingId, UUID moderatorId, ModerateListingRequest request);


    /**
     * Получает любое объявление по ID для просмотра модератором.
     * @param listingId ID объявления.
     * @return DTO объявления.
     */
    ModerationListingResponse getListingForModeration(UUID listingId);
}
package ru.ecosharing.listing_service.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.ecosharing.listing_service.client.UserServiceClient; // Если нужна информация о модераторе
import ru.ecosharing.listing_service.dto.request.ModerateListingRequest;
import ru.ecosharing.listing_service.dto.response.ListingResponse;
import ru.ecosharing.listing_service.dto.response.ModerationListingResponse;
import ru.ecosharing.listing_service.dto.client.PublicUserProfileResponse;
import ru.ecosharing.listing_service.dto.response.UserSummaryDto;
import ru.ecosharing.listing_service.enums.ModerationStatus;
import ru.ecosharing.listing_service.exception.ListingOperationException;
import ru.ecosharing.listing_service.exception.ResourceNotFoundException;
import ru.ecosharing.listing_service.dto.kafka.ListingModerationStatusChangedEvent;
import ru.ecosharing.listing_service.kafka.producer.ListingEventProducer;
import ru.ecosharing.listing_service.mapper.ListingMapper; // Используем тот же маппер
import ru.ecosharing.listing_service.model.Listing;
import ru.ecosharing.listing_service.repository.FavoriteListingRepository;
import ru.ecosharing.listing_service.repository.ListingRepository;
import ru.ecosharing.listing_service.service.ModerationService;


import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ModerationServiceImpl implements ModerationService {

    private final ListingRepository listingRepository;
    private final ListingMapper listingMapper;
    private final ListingEventProducer eventProducer;
    private final UserServiceClient userServiceClient; // Для получения информации о владельце
    private final FavoriteListingRepository favoriteListingRepository; // Для флага isFavorite

    // private final NotificationServiceInternalClient notificationClient; // Если отправляем уведомления напрямую

    @Override
    @Transactional(readOnly = true)
    public Page<ModerationListingResponse> getPendingModerationListings(Pageable pageable) {
        log.debug("Fetching listings pending moderation, pageable: {}", pageable);
        Page<Listing> listingsPage = listingRepository.findAllByModerationStatus(
                ModerationStatus.PENDING_MODERATION,
                pageable
        );
        return listingsPage.map(listing -> buildModerationListingResponse(listing, null)); // moderatorId не нужен для списка
    }

    @Override
    @Transactional
    public ListingResponse approveListing(UUID listingId, UUID moderatorId) {
        log.info("Moderator {} approving listing ID: {}", moderatorId, listingId);
        Listing listing = findListingForModerationInternal(listingId);

        if (listing.getModerationStatus() != ModerationStatus.PENDING_MODERATION &&
                listing.getModerationStatus() != ModerationStatus.NEEDS_REVISION) { // Можно одобрить и то, что было на доработке
            throw new ListingOperationException("Объявление ID " + listingId + " не может быть одобрено из текущего статуса: " + listing.getModerationStatus());
        }

        ModerationStatus oldStatus = listing.getModerationStatus();
        listing.setModerationStatus(ModerationStatus.ACTIVE);
        listing.setModerationComment(null); // Очищаем комментарии, если были
        listing.setRejectionReason(null);
        listing.setLastModeratedAt(LocalDateTime.now());
        // listing.setLastModeratorId(moderatorId); // Если бы хранили ID модератора в Listing

        Listing updatedListing = listingRepository.save(listing);
        log.info("Listing ID: {} approved by moderator {}. New status: ACTIVE", listingId, moderatorId);

        eventProducer.sendListingModerationStatusChangedEvent(
                new ListingModerationStatusChangedEvent(listingId, ModerationStatus.ACTIVE, oldStatus, moderatorId)
        );

        // Отправка уведомления пользователю об одобрении
        // sendUserNotification(updatedListing, NotificationType.LISTING_APPROVED, moderatorId, null);

        return buildListingResponse(updatedListing, null); // currentUserId null, т.к. это действие модератора
    }

    @Override
    @Transactional
    public ListingResponse sendListingForRevision(UUID listingId, UUID moderatorId, ModerateListingRequest request) {
        log.info("Moderator {} sending listing ID: {} for revision. Comment: {}", moderatorId, listingId, request.getModerationComment());
        if (request.getModerationComment() == null || request.getModerationComment().isBlank()) {
            throw new ListingOperationException("Комментарий модератора обязателен при отправке на доработку.");
        }
        Listing listing = findListingForModerationInternal(listingId);

        if (listing.getModerationStatus() == ModerationStatus.ACTIVE ||
                listing.getModerationStatus() == ModerationStatus.REJECTED) {
            throw new ListingOperationException("Объявление ID " + listingId + " не может быть отправлено на доработку из статуса " + listing.getModerationStatus());
        }

        ModerationStatus oldStatus = listing.getModerationStatus();
        listing.setModerationStatus(ModerationStatus.NEEDS_REVISION);
        listing.setModerationComment(request.getModerationComment());
        listing.setRejectionReason(request.getRejectionReason()); // Может быть null
        listing.setLastModeratedAt(LocalDateTime.now());

        Listing updatedListing = listingRepository.save(listing);
        log.info("Listing ID: {} sent for revision by moderator {}. New status: NEEDS_REVISION", listingId, moderatorId);

        eventProducer.sendListingModerationStatusChangedEvent(
                new ListingModerationStatusChangedEvent(listingId, ModerationStatus.NEEDS_REVISION, oldStatus, moderatorId)
        );

        // Отправка уведомления пользователю о необходимости доработки
        // Map<String, String> params = Map.of(
        // "listingTitle", updatedListing.getTitle(),
        // "moderatorComment", request.getModerationComment(),
        // "rejectionReason", request.getRejectionReason() != null ? request.getRejectionReason() : "Не указана"
        // );
        // sendUserNotification(updatedListing, NotificationType.LISTING_NEEDS_REVISION, moderatorId, params);


        return buildListingResponse(updatedListing, null);
    }

    @Override
    @Transactional
    public ListingResponse rejectListing(UUID listingId, UUID moderatorId, ModerateListingRequest request) {
        log.warn("Moderator {} rejecting listing ID: {}. Reason: {}", moderatorId, listingId, request.getRejectionReason());
        if (request.getModerationComment() == null || request.getModerationComment().isBlank()) { // Комментарий тоже важен
            throw new ListingOperationException("Комментарий модератора обязателен при отклонении.");
        }
        Listing listing = findListingForModerationInternal(listingId);

        if (listing.getModerationStatus() == ModerationStatus.REJECTED ||
                listing.getModerationStatus() == ModerationStatus.ACTIVE) { // Нельзя отклонить уже активное без предварительного перевода в другой статус
            throw new ListingOperationException("Объявление ID " + listingId + " не может быть отклонено из статуса " + listing.getModerationStatus());
        }

        ModerationStatus oldStatus = listing.getModerationStatus();
        listing.setModerationStatus(ModerationStatus.REJECTED);
        listing.setModerationComment(request.getModerationComment());
        listing.setRejectionReason(request.getRejectionReason());
        listing.setLastModeratedAt(LocalDateTime.now());

        Listing updatedListing = listingRepository.save(listing);
        log.info("Listing ID: {} rejected by moderator {}. New status: REJECTED", listingId, moderatorId);

        eventProducer.sendListingModerationStatusChangedEvent(
                new ListingModerationStatusChangedEvent(listingId, ModerationStatus.REJECTED, oldStatus, moderatorId)
        );

        // Отправка уведомления пользователю об отклонении
        // Map<String, String> params = Map.of(
        // "listingTitle", updatedListing.getTitle(),
        // "moderatorComment", request.getModerationComment(),
        // "rejectionReason", request.getRejectionReason() != null ? request.getRejectionReason() : "Не указана"
        // );
        // sendUserNotification(updatedListing, NotificationType.LISTING_REJECTED, moderatorId, params);

        return buildListingResponse(updatedListing, null);
    }

    @Override
    @Transactional(readOnly = true)
    public ModerationListingResponse getListingForModeration(UUID listingId) {
        log.debug("Moderator fetching listing ID: {}", listingId);
        Listing listing = findListingForModerationInternal(listingId);
        return buildModerationListingResponse(listing, null); // moderatorId не важен для простого просмотра
    }


    // --- Вспомогательные методы ---

    private Listing findListingForModerationInternal(UUID listingId) {
        return listingRepository.findById(listingId)
                .orElseThrow(() -> new ResourceNotFoundException("Объявление с ID " + listingId + " не найдено для модерации."));
    }

    // Общий метод для сборки DTO ответа, используется и ListingServiceImpl
    // Если ModerationListingResponse будет отличаться, то нужен отдельный метод или доп. логика
    private ListingResponse buildListingResponse(Listing listing, UUID currentUserId) {
        ListingResponse response = listingMapper.toListingResponse(listing);
        UserSummaryDto ownerInfo = fetchAndBuildOwnerSummary(listing.getUserId());
        response.setOwner(ownerInfo);

        if (currentUserId != null) {
            response.setFavorite(favoriteListingRepository.existsById_UserIdAndId_ListingId(currentUserId, listing.getId()));
        } else {
            response.setFavorite(false); // Для модератора или системных вызовов isFavorite нерелевантен
        }
        return response;
    }

    // Если ModerationListingResponse будет иметь свои поля, то нужен этот метод
    private ModerationListingResponse buildModerationListingResponse(Listing listing, UUID moderatorId) {
        // Пока он идентичен buildListingResponse, но маппер может быть другим
        // или здесь можно добавить поля, специфичные для модератора
        UserSummaryDto ownerInfo = fetchAndBuildOwnerSummary(listing.getUserId());

        // Используем ListingMapper, который вернет ListingResponse, а затем создадим ModerationListingResponse
        // Это не оптимально, лучше иметь прямой маппинг Listing -> ModerationListingResponse
        // Для примера, пока сделаем так:
        ListingResponse tempResponse = listingMapper.toListingResponse(listing);

        return ModerationListingResponse.builder()
                .id(tempResponse.getId())
                .title(tempResponse.getTitle())
                .description(tempResponse.getDescription())
                .category(tempResponse.getCategory())
                .mainImageUrl(tempResponse.getMainImageUrl())
                .additionalImageUrls(tempResponse.getAdditionalImageUrls())
                .locationText(tempResponse.getLocationText())
                .price(tempResponse.getPrice())
                .currency(tempResponse.getCurrency())
                .priceType(tempResponse.getPriceType())
                .moderationStatus(tempResponse.getModerationStatus())
                .availabilityStatus(tempResponse.getAvailabilityStatus())
                .moderationComment(tempResponse.getModerationComment())
                .rejectionReason(tempResponse.getRejectionReason())
                .viewCount(tempResponse.getViewCount())
                .createdAt(tempResponse.getCreatedAt())
                .updatedAt(tempResponse.getUpdatedAt())
                .lastModeratedAt(tempResponse.getLastModeratedAt())
                // .lastModeratorId(listing.getLastModeratorId()) // Если бы хранили
                .build();
    }


    private UserSummaryDto fetchAndBuildOwnerSummary(UUID ownerId) {
        // Этот метод уже есть в ListingServiceImpl, можно его вынести в общий хелпер или дублировать.
        // Для простоты здесь будет его упрощенная версия или ссылка на то, что он должен быть таким же.
        try {
            // PublicUserProfileResponse userProfileResponse = userServiceClient.getPublicUserProfile(ownerId);
            // Mock:
            PublicUserProfileResponse userProfileResponse = PublicUserProfileResponse.builder()
                    .userId(ownerId)
                    .username("owner_" + ownerId.toString().substring(0,4))
                    .firstName("Владелец")
                    .avatarUrl("http://example.com/owner_avatar.jpg")
                    .build();

            if (userProfileResponse != null) {
                return UserSummaryDto.builder()
                        .userId(userProfileResponse.getUserId())
                        .username(userProfileResponse.getUsername())
                        .firstName(userProfileResponse.getFirstName())
                        .avatarUrl(userProfileResponse.getAvatarUrl())
                        .build();
            }
        } catch (Exception e) {
            log.error("ModerationService: Failed to fetch owner ({}) details from User Service: {}", ownerId, e.getMessage());
        }
        return UserSummaryDto.builder().userId(ownerId).username("Владелец " + ownerId.toString().substring(0,4)).build();
    }

    // Метод для отправки уведомлений (пример, как это могло бы быть)
    /*
    private void sendUserNotification(Listing listing, NotificationType type, UUID actionByUserId, Map<String, String> params) {
        if (listing.getUserId().equals(actionByUserId)) {
            log.warn("Attempting to send notification to self for listing {}, action by user {}. Skipping.", listing.getId(), actionByUserId);
            return; // Не шлем уведомление самому себе (например, модератор - владелец)
        }

        NotificationRequestDto.Builder notificationBuilder = NotificationRequestDto.builder()
                .userId(listing.getUserId()) // Получатель - владелец объявления
                .notificationType(type)
                .params(params != null ? params : new HashMap<>());

        // Добавляем стандартные параметры
        notificationBuilder.param("listingId", listing.getId().toString());
        if (listing.getTitle() != null) {
             notificationBuilder.param("listingTitle", listing.getTitle());
        }
        if (actionByUserId != null) {
            // Можно запросить имя модератора, если нужно
            notificationBuilder.param("moderatorId", actionByUserId.toString());
        }
        // notificationBuilder.targetUrl(...); // Ссылка на объявление

        try {
            // eventProducer.sendNotificationRequest(notificationBuilder.build());
            log.info("Sent notification type {} for listing {} to user {}", type, listing.getId(), listing.getUserId());
        } catch (Exception e) {
            log.error("Error sending notification for listing {}: {}", listing.getId(), e.getMessage(), e);
        }
    }
    */
}
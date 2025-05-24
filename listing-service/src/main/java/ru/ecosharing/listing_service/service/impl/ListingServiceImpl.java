package ru.ecosharing.listing_service.service.impl;

import co.elastic.clients.elasticsearch._types.query_dsl.*;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.json.JsonData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.ecosharing.listing_service.client.UserServiceClient; // Feign клиент к User Service
import ru.ecosharing.listing_service.dto.request.CreateListingRequest;
import ru.ecosharing.listing_service.dto.request.UpdateListingRequest;
import ru.ecosharing.listing_service.dto.response.ListingResponse;
import ru.ecosharing.listing_service.dto.response.ListingSummaryResponse;
import ru.ecosharing.listing_service.dto.client.PublicUserProfileResponse; // Ожидаем от UserServiceClient
import ru.ecosharing.listing_service.dto.response.UserSummaryDto; // Наш DTO для owner в ListingResponse
import ru.ecosharing.listing_service.elasticsearch.document.ListingDocument;
import ru.ecosharing.listing_service.elasticsearch.repository.ListingSearchRepository;
import ru.ecosharing.listing_service.enums.AvailabilityStatus;
import ru.ecosharing.listing_service.enums.ModerationStatus;
import ru.ecosharing.listing_service.exception.ListingOperationException;
import ru.ecosharing.listing_service.exception.ResourceNotFoundException;
import ru.ecosharing.listing_service.dto.kafka.*;
import ru.ecosharing.listing_service.kafka.producer.ListingEventProducer;
import ru.ecosharing.listing_service.mapper.ListingMapper;
import ru.ecosharing.listing_service.model.Category;
import ru.ecosharing.listing_service.model.FavoriteListing;
import ru.ecosharing.listing_service.model.FavoriteListingId;
import ru.ecosharing.listing_service.model.Listing;
import ru.ecosharing.listing_service.repository.FavoriteListingRepository;
import ru.ecosharing.listing_service.repository.ListingRepository;
import ru.ecosharing.listing_service.service.CategoryService;
import ru.ecosharing.listing_service.service.ListingService;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ListingServiceImpl implements ListingService {

    private final ListingRepository listingRepository;
    private final ListingMapper listingMapper;
    private final CategoryService categoryService; // Для получения сущности Category
    private final FavoriteListingRepository favoriteListingRepository;
    private final ListingEventProducer eventProducer;
    private final ElasticsearchOperations elasticsearchOperations; // Для сложных запросов к ES
    private final ListingSearchRepository listingSearchRepository; // Для простых запросов или если нужен репозиторий
    private final UserServiceClient userServiceClient; // Feign-клиент

    // --- Пользовательские операции ---

    @Override
    @Transactional
    public ListingResponse createListing(UUID userId, CreateListingRequest request) {
        log.info("User {} creating new listing with title: {}", userId, request.getTitle());
        Category category = categoryService.findCategoryEntityById(request.getCategoryId());
        if (!category.isActive()) {
            throw new ListingOperationException("Нельзя создать объявление в неактивной категории: " + category.getName());
        }

        Listing listing = listingMapper.toListing(request);
        listing.setUserId(userId);
        listing.setCategory(category); // Устанавливаем полную сущность категории
        listing.setModerationStatus(ModerationStatus.PENDING_MODERATION);
        listing.setAvailabilityStatus(AvailabilityStatus.AVAILABLE);

        Listing persistedListing = listingRepository.saveAndFlush(listing);
        Listing savedListing = listingRepository.findById(persistedListing.getId()).orElse(null);
        log.info("CREATED " + savedListing.getCreatedAt());
        log.info("Listing created with ID: {} by user {}", savedListing.getId(), userId);

        UserSummaryDto ownerInfoForEvent = fetchAndBuildOwnerSummary(userId);

        eventProducer.sendListingCreatedEvent(
                new ListingCreatedEvent(
                        savedListing.getId(),
                        savedListing.getUserId(),
                        savedListing.getTitle(),
                        savedListing.getDescription(),
                        savedListing.getCategory().getId(),
                        savedListing.getMainImageUrl(),
                        savedListing.getAdditionalImageUrls(),
                        savedListing.getLocationText(),
                        savedListing.getPrice(),
                        savedListing.getCurrency(),
                        savedListing.getPriceType(),
                        savedListing.getCreatedAt()
                )
        );

        return buildListingResponse(savedListing, userId);
    }

    @Override
    @Transactional
    public ListingResponse updateMyListing(UUID userId, UUID listingId, UpdateListingRequest request) {
        log.info("User {} updating listing ID: {}", userId, listingId);
        Listing listing = findMyListingByIdInternal(userId, listingId);

        // Проверяем, можно ли редактировать объявление в текущем статусе модерации
        if (listing.getModerationStatus() == ModerationStatus.REJECTED) {
            throw new ListingOperationException("Отклоненное объявление не может быть отредактировано. Создайте новое.");
        }

        // Сохраняем старый статус для определения, нужна ли повторная модерация
        ModerationStatus oldModerationStatus = listing.getModerationStatus();
        boolean criticalFieldsChanged = detectCriticalFieldChanges(request, listing);

        listingMapper.updateListingFromDto(request, listing);

        // Если категория меняется, проверяем ее активность
        if (request.getCategoryId() != null && !request.getCategoryId().equals(listing.getCategory().getId())) {
            Category newCategory = categoryService.findCategoryEntityById(request.getCategoryId());
            if (!newCategory.isActive()) {
                throw new ListingOperationException("Нельзя выбрать неактивную категорию: " + newCategory.getName());
            }
            listing.setCategory(newCategory);
        }

        // Логика изменения статуса модерации
        if (oldModerationStatus == ModerationStatus.ACTIVE && criticalFieldsChanged) {
            listing.setModerationStatus(ModerationStatus.PENDING_MODERATION);
            listing.setModerationComment(null); // Сбрасываем старые комментарии модератора
            listing.setRejectionReason(null);
        } else if (oldModerationStatus == ModerationStatus.NEEDS_REVISION) {
            listing.setModerationStatus(ModerationStatus.PENDING_MODERATION);
            listing.setModerationComment(null);
            listing.setRejectionReason(null);
        }
        // Если статус был PENDING_MODERATION или INACTIVE, он таким и останется (или станет PENDING_MODERATION, если INACTIVE и были правки)

        Listing updatedListing = listingRepository.save(listing);
        log.info("Listing ID: {} updated by user {}", listingId, userId);

        // Отправка события в Kafka
        eventProducer.sendListingUpdatedEvent(
                new ListingUpdatedEvent(
                        updatedListing.getId(),
                        updatedListing.getTitle(), // Отправляем все поля, которые могли измениться и важны для ES
                        updatedListing.getDescription(),
                        updatedListing.getCategory().getId(),
                        updatedListing.getMainImageUrl(),
                        updatedListing.getAdditionalImageUrls(),
                        updatedListing.getLocationText(),
                        updatedListing.getPrice(),
                        updatedListing.getCurrency(),
                        updatedListing.getPriceType()
                )
        );
        // Если изменился статус модерации, также отправить ListingModerationStatusChangedEvent
        if (oldModerationStatus != updatedListing.getModerationStatus()) {
            eventProducer.sendListingModerationStatusChangedEvent(
                    updatedListing.getId(),
                    updatedListing.getModerationStatus(),
                    oldModerationStatus,
                    null // moderatorId - нет, т.к. это действие пользователя
            );
        }

        return buildListingResponse(updatedListing, userId);
    }

    private boolean detectCriticalFieldChanges(UpdateListingRequest request, Listing listing) {
        // Определяем, изменились ли поля, требующие повторной модерации
        if (request.getTitle() != null && !request.getTitle().equals(listing.getTitle())) return true;
        if (request.getDescription() != null && !request.getDescription().equals(listing.getDescription())) return true;
        if (request.getCategoryId() != null && !request.getCategoryId().equals(listing.getCategory().getId())) return true;
        if (request.getMainImageUrl() != null && !Objects.equals(request.getMainImageUrl(), listing.getMainImageUrl())) return true;
        // Изменение цены или типа цены обычно тоже требует проверки
        if (request.getPrice() != null && request.getPrice().compareTo(listing.getPrice()) != 0) return true;
        if (request.getPriceType() != null && request.getPriceType() != listing.getPriceType()) return true;
        // Дополнительные изображения
        if (request.getAdditionalImageUrls() != null && !areImageListsEqual(request.getAdditionalImageUrls(), listing.getAdditionalImageUrls())) return true;

        return false;
    }

    private boolean areImageListsEqual(List<String> list1, List<String> list2) {
        if (list1 == null && list2 == null) return true;
        if (list1 == null || list2 == null) return false;
        return list1.size() == list2.size() && list1.containsAll(list2) && list2.containsAll(list1);
    }


    @Override
    @Transactional
    public void deleteMyListing(UUID userId, UUID listingId) {
        log.warn("User {} attempting to delete listing ID: {}", userId, listingId);
        Listing listing = findMyListingByIdInternal(userId, listingId);
        listingRepository.delete(listing); // Физическое удаление
        log.info("Listing ID: {} physically deleted by user {}", listingId, userId);

        eventProducer.sendListingDeletedEvent(new ListingDeletedEvent(listingId));
    }

    @Override
    @Transactional
    public ListingResponse getListingById(UUID listingId, UUID currentUserId) {
        log.debug("Fetching listing ID: {}, current user ID: {}", listingId, currentUserId);
        Listing listing = listingRepository.findById(listingId)
                .orElseThrow(() -> new ResourceNotFoundException("Объявление с ID " + listingId + " не найдено."));

        // Проверка доступа
        boolean canView = false;
        if (listing.getModerationStatus() == ModerationStatus.ACTIVE) {
            canView = true;
        } else if (currentUserId != null && listing.getUserId().equals(currentUserId)) {
            // Владелец может видеть свое объявление в любом статусе (кроме REJECTED, если решим)
            canView = true;
        }
        // Модераторы/админы будут использовать свои эндпоинты с другими проверками

        if (!canView) {
            log.warn("User {} attempted to access restricted listing {}", currentUserId, listingId);
            throw new AccessDeniedException("У вас нет прав на просмотр этого объявления.");
        }

        // Инкремент счетчика просмотров (только если это не владелец смотрит свое же объявление)
        if (currentUserId == null || !listing.getUserId().equals(currentUserId)) {
            listingRepository.incrementViewCount(listingId);
            listing.setViewCount(listing.getViewCount() + 1); // Обновляем для DTO, т.к. incrementViewCount не возвращает
            eventProducer.sendListingViewCountIncrementedEvent(new ListingViewCountIncrementedEvent(listingId, listing.getViewCount()));
        }

        return buildListingResponse(listing, currentUserId);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ListingSummaryResponse> getMyListings(UUID userId, Pageable pageable) {
        log.debug("Fetching listings for user ID: {}, pageable: {}", userId, pageable);
        // Используем PostgreSQL для "Моих объявлений", так как там всегда актуальные статусы
        Page<Listing> listingsPage = listingRepository.findAllByUserId(userId, pageable);
        // Маппинг в DTO, обогащение ownerUsername/ownerAvatarUrl, если нужно для этого списка
        return listingsPage.map(listing -> {
            ListingSummaryResponse summary = listingMapper.toListingSummaryResponse(listing);
            // Для "моих" объявлений мы знаем владельца, но если UserSummaryDto сложный,
            // может быть проще просто передать username, если он есть в Listing
            // Или если ListingSummaryResponse должен быть единообразным, нужно решить,
            // как получать ownerUsername и ownerAvatarUrl для этого случая.
            // Предположим, что для этого списка достаточно ID владельца.
            // А если нужен username/avatar - клиент запросит профиль.
            // Или, если мы денормализуем username в Listing (не рекомендуется), то можно взять оттуда.
            // Пока оставим ownerUsername/AvatarUrl пустыми, если они не в listingMapper.
            return summary;
        });
    }

    @Override
    @Transactional
    public ListingResponse activateMyListing(UUID userId, UUID listingId) {
        log.info("User {} activating listing ID: {}", userId, listingId);
        Listing listing = findMyListingByIdInternal(userId, listingId);

        if (listing.getModerationStatus() == ModerationStatus.REJECTED) {
            throw new ListingOperationException("Отклоненное объявление не может быть активировано.");
        }
        if (listing.getModerationStatus() == ModerationStatus.ACTIVE && listing.getAvailabilityStatus() == AvailabilityStatus.AVAILABLE) {
            return buildListingResponse(listing, userId); // Уже активно и доступно
        }

        // Если ранее было одобрено (ACTIVE или NEEDS_REVISION после правок) и сейчас INACTIVE
        // или если это PENDING_MODERATION и пользователь просто "активирует" его для отправки на модерацию.
        // Главное - перевести в статус, который виден системе модерации или пользователям.
        // Если было INACTIVE, а до этого ACTIVE -> делаем ACTIVE
        // Если было INACTIVE, а до этого PENDING/NEEDS_REVISION -> делаем PENDING_MODERATION
        // Для простоты: если не PENDING и не ACTIVE, то ставим PENDING

        ModerationStatus oldModStatus = listing.getModerationStatus();
        // Если объявление было одобрено ранее (ACTIVE) и его деактивировал пользователь,
        // то при активации оно сразу становится ACTIVE.
        // Если оно еще не проходило модерацию или требует правок, оно уходит на модерацию.
        if (listing.getModerationStatus() == ModerationStatus.INACTIVE) {
            // Проверяем, нужно ли отправлять на повторную модерацию (если были критичные изменения после последнего одобрения)
            // Эта логика сложна без истории изменений. Проще: если ранее было ACTIVE, то ACTIVE, иначе PENDING.
            // Допустим, у нас нет такой тонкой логики "ранее было ACTIVE".
            // Тогда любая активация из INACTIVE, если объявление не было REJECTED, отправляет на модерацию.
            // Но если мы храним lastModeratedAt и оно было ACTIVE, можно вернуть в ACTIVE.
            // Пока упростим: из INACTIVE всегда в PENDING_MODERATION, если оно не REJECTED
            listing.setModerationStatus(ModerationStatus.PENDING_MODERATION);
        } else if (listing.getModerationStatus() != ModerationStatus.ACTIVE) {
            listing.setModerationStatus(ModerationStatus.PENDING_MODERATION);
        }

        listing.setAvailabilityStatus(AvailabilityStatus.AVAILABLE); // При активации делаем доступным
        Listing updatedListing = listingRepository.save(listing);

        if (oldModStatus != updatedListing.getModerationStatus()) {
            eventProducer.sendListingModerationStatusChangedEvent(
                    updatedListing.getId(), updatedListing.getModerationStatus(), oldModStatus, null);
        }
        eventProducer.sendListingAvailabilityStatusChangedEvent(updatedListing.getId(), updatedListing.getAvailabilityStatus());

        log.info("Listing ID: {} activated by user {}. New status: {}", listingId, userId, updatedListing.getModerationStatus());
        return buildListingResponse(updatedListing, userId);
    }

    @Override
    @Transactional
    public ListingResponse deactivateMyListing(UUID userId, UUID listingId) {
        log.info("User {} deactivating listing ID: {}", userId, listingId);
        Listing listing = findMyListingByIdInternal(userId, listingId);

        if (listing.getModerationStatus() == ModerationStatus.INACTIVE) {
            return buildListingResponse(listing, userId); // Уже неактивно
        }
        ModerationStatus oldModStatus = listing.getModerationStatus();

        listing.setModerationStatus(ModerationStatus.INACTIVE);
        // AvailabilityStatus можно не менять, т.к. INACTIVE имеет приоритет по видимости
        Listing updatedListing = listingRepository.save(listing);

        if (oldModStatus != updatedListing.getModerationStatus()) {
            eventProducer.sendListingModerationStatusChangedEvent(
                    updatedListing.getId(), updatedListing.getModerationStatus(), oldModStatus, null);
        }

        log.info("Listing ID: {} deactivated by user {}", listingId, userId);
        return buildListingResponse(updatedListing, userId);
    }

    // --- Публичные операции (поиск) ---

    /**
     * Search listings with multiple filters and pagination using the new Spring Data Elasticsearch NativeQuery API.
     *
     * @param categoryId         Filter by category ID (optional)
     * @param searchTerm         Full-text search term for title and description (optional)
     * @param locationText       Filter by location text (optional)
     * @param priceFrom          Minimum price (optional)
     * @param priceTo            Maximum price (optional)
     * @param availabilityStatus Filter by availability status (optional)
     * @param pageable           Paging and sorting info
     * @return paged list of filtered listing summaries
     */
    @Override
    @Transactional(readOnly = true)
    public Page<ListingSummaryResponse> searchListings(
            UUID categoryId,
            String searchTerm,
            String locationText,
            BigDecimal priceFrom,
            BigDecimal priceTo,
            AvailabilityStatus availabilityStatus,
            Pageable pageable) {

        log.debug("Public search for listings using NativeQuery. Term: '{}', Category: {}, Location: '{}', PriceFrom: {}, PriceTo: {}, Availability: {}, Pageable: {}",
                searchTerm, categoryId, locationText, priceFrom, priceTo, availabilityStatus, pageable);

        NativeQuery searchQuery = NativeQuery.builder()
                .withQuery(q -> q.bool(b -> {
                    b.filter(filterQuery -> filterQuery
                            .term(t -> t.field("moderationStatus").value(ModerationStatus.PENDING_MODERATION.name())));

                    // Фильтр по categoryId
                    if (categoryId != null) {
                        b.filter(filterQuery -> filterQuery
                                .term(t -> t.field("categoryId").value(categoryId.toString())));
                    }

                    // Фильтр по availabilityStatus
                    if (availabilityStatus != null) {
                        b.filter(filterQuery -> filterQuery
                                .term(t -> t.field("availabilityStatus").value(availabilityStatus.name())));
                    }

                    // Фильтр по цене
                    if (priceFrom != null || priceTo != null) {
                        b.filter(RangeQuery.of(rq -> rq
                                .number(n -> {
                                    NumberRangeQuery.Builder nb = new NumberRangeQuery.Builder();
                                    nb.field("price");
                                    if (priceFrom != null) nb.gte(priceFrom.doubleValue());
                                    if (priceTo != null) nb.lte(priceTo.doubleValue());
                                    return nb;
                                })
                        )._toQuery());
                    }

                    // --- Условия, которые должны выполняться (влияют на score) ---
                    List<Query> mustClauses = new ArrayList<>();
                    List<Query> shouldClausesForPrefix = new ArrayList<>();

                    // Full text multi-match search с fuzziness на title и description
                    if (searchTerm != null && !searchTerm.isBlank()) {
                        mustClauses.add(MultiMatchQuery.of(mmq -> mmq
                                .query(searchTerm)
                                .fields("title^3", "description") // Вес для title больше
//                                .fuzziness("AUTO") // Строковое значение "AUTO" должно работать
//                                .operator(Operator.Or) // Искать все слова из searchTerm
                                .type(TextQueryType.PhrasePrefix)
                        )._toQuery()); // Преобразуем MultiMatchQuery в Query
                        shouldClausesForPrefix.add(MatchPhrasePrefixQuery.of(mpq -> mpq
                                        .field("title")
                                        .query(searchTerm.toLowerCase())) // searchTerm для префикса лучше в нижнем регистре, если анализатор это делает
                                ._toQuery());
                        shouldClausesForPrefix.add(MatchPhrasePrefixQuery.of(mpq -> mpq
                                        .field("description")
                                        .query(searchTerm.toLowerCase()))
                                ._toQuery());
                    }


                    // Фильтр по locationText (используем match_phrase_prefix для поиска по началу фразы)
                    if (locationText != null && !locationText.isBlank()) {
                        shouldClausesForPrefix.add(MatchPhrasePrefixQuery.of(mpq -> mpq
                                        .field("locationText")
                                        .query(locationText.toLowerCase()))
                                ._toQuery()); // Преобразуем MatchPhrasePrefixQuery в Query
                    }
                    if (!shouldClausesForPrefix.isEmpty()) {
                        b.should(shouldClausesForPrefix);
                    }
                    if (!mustClauses.isEmpty()) {
                        b.must(mustClauses);
                    } else {
                        b.must(ma -> ma.matchAll(mAll -> mAll)); // MatchAllQuery
                    }

                    return b; // Возвращаем построенный BoolQuery.Builder
                }))
                .withPageable(pageable) // Применяем пагинацию и сортировку
                .build();

        log.debug("Executing Elasticsearch NativeQuery (JSON): {}", searchQuery.getQuery().toString());
        // Для отладки самого запроса, можно его получить так:
        // if (searchQuery.getQuery() != null && searchQuery.getQuery().isBool()) {
        //    log.debug("NativeQuery JSON: " + searchQuery.getQuery().bool().toString());
        // }

        SearchHits<ListingDocument> searchHits = elasticsearchOperations.search(searchQuery, ListingDocument.class);

        List<ListingSummaryResponse> results = searchHits.getSearchHits().stream()
                .map(SearchHit::getContent)
                .map(doc -> {
                    ListingSummaryResponse summary = listingMapper.toListingSummaryResponse(mapDocumentToJpaLikeForSummary(doc));
                    summary.setOwnerUsername(doc.getOwnerUsername());
                    summary.setFavorite(false);
                    return summary;
                })
                .collect(Collectors.toList());

        return new PageImpl<>(results, pageable, searchHits.getTotalHits());
    }

    /**
     * Temporary mapper method: maps ListingDocument to domain Listing entity style for compatibility with existing mapper.
     */
    private Listing mapDocumentToJpaLikeForSummary(ListingDocument doc) {
        Category cat = new Category();
        cat.setId(doc.getCategoryId());
        cat.setName(doc.getCategoryName());

        return Listing.builder()
                .id(UUID.fromString(doc.getId()))
                .title(doc.getTitle())
                .mainImageUrl(doc.getMainImageUrl())
                .locationText(doc.getLocationText())
                .price(doc.getPrice())
                .currency(doc.getCurrency())
                .priceType(doc.getPriceType())
                .availabilityStatus(doc.getAvailabilityStatus())
                .createdAt(doc.getCreatedAt())
                .viewCount(doc.getViewCount())
                .userId(doc.getOwnerUserId())
                .category(cat)
                .build();
    }



    @Override
    @Transactional(readOnly = true)
    public Page<ListingSummaryResponse> getListingsByOwner(UUID ownerUserId, Pageable pageable) {
        log.debug("Fetching listings for owner ID: {}, pageable: {}", ownerUserId, pageable);
        // Ищем в Elasticsearch по ownerUserId и только активные
        Criteria criteria = new Criteria("ownerUserId").is(ownerUserId)
                .and(new Criteria("moderationStatus").is(ModerationStatus.ACTIVE.name()));
        CriteriaQuery query = new CriteriaQuery(criteria, pageable);

        SearchHits<ListingDocument> searchHits = elasticsearchOperations.search(query, ListingDocument.class);
        List<ListingSummaryResponse> summaries = searchHits.getSearchHits().stream()
                .map(SearchHit::getContent)
                .map(doc -> listingMapper.toListingSummaryResponse(mapDocumentToJpaLikeForSummary(doc)))
                .collect(Collectors.toList());
        return new PageImpl<>(summaries, pageable, searchHits.getTotalHits());
    }


    // --- Операции с избранным ---

    @Override
    @Transactional
    public void addListingToFavorites(UUID userId, UUID listingId) {
        log.info("User {} adding listing {} to favorites", userId, listingId);
        // Проверяем, существует ли объявление и активно ли оно
        Listing listing = listingRepository.findById(listingId)
                .filter(l -> l.getModerationStatus() == ModerationStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("Активное объявление с ID " + listingId + " не найдено."));

        FavoriteListingId favoriteId = new FavoriteListingId(userId, listingId);
        if (favoriteListingRepository.existsById(favoriteId)) {
            log.debug("Listing {} is already in favorites for user {}", listingId, userId);
            return; // Уже в избранном
        }
        FavoriteListing favorite = FavoriteListing.builder().id(favoriteId).build();
        favoriteListingRepository.save(favorite);
        log.info("Listing {} added to favorites for user {}", listingId, userId);
    }

    @Override
    @Transactional
    public void removeListingFromFavorites(UUID userId, UUID listingId) {
        log.info("User {} removing listing {} from favorites", userId, listingId);
        FavoriteListingId favoriteId = new FavoriteListingId(userId, listingId);
        favoriteListingRepository.deleteById(favoriteId); // Удалит, если существует
        log.info("Listing {} removed from favorites for user {} (if existed)", listingId, userId);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ListingSummaryResponse> getFavoriteListings(UUID userId, Pageable pageable) {
        log.debug("Fetching favorite listings for user ID: {}, pageable: {}", userId, pageable);
        // Этот метод в FavoriteListingRepository уже возвращает Page<Listing>
        // и фильтрует по isDeleted=false и активным объявлениям (нужно проверить реализацию репозитория)
        // Для единообразия и использования ES, можно было бы получать ID из favoriteListingRepository,
        // а затем делать запрос в ES. Но прямой JOIN в PostgreSQL для этого случая может быть эффективнее.
        // Пока оставим так, как есть в репозитории (возвращает Page<Listing>).

        Page<Listing> favoriteListingsPage = favoriteListingRepository.findFavoriteListingsByUserId(userId, pageable);

        return favoriteListingsPage.map(listing -> {
            ListingSummaryResponse summary = listingMapper.toListingSummaryResponse(listing);
            // Здесь также нужно подумать про ownerUsername/ownerAvatarUrl,
            // если они нужны и маппер их не заполняет из JPA Listing.
            return summary;
        });
    }

    // --- Вспомогательные методы ---

    private Listing findMyListingByIdInternal(UUID userId, UUID listingId) {
        return listingRepository.findByIdAndUserId(listingId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Объявление с ID " + listingId + " не найдено или не принадлежит пользователю " + userId));
    }

    private ListingResponse buildListingResponse(Listing listing, UUID currentUserId) {
        ListingResponse response = listingMapper.toListingResponse(listing);

        // Обогащаем информацией о владельце
        UserSummaryDto ownerInfo = fetchAndBuildOwnerSummary(listing.getUserId());
        response.setOwner(ownerInfo);

        // Проверяем, в избранном ли оно у текущего пользователя (если он есть)
        if (currentUserId != null) {
            response.setFavorite(favoriteListingRepository.existsById_UserIdAndId_ListingId(currentUserId, listing.getId()));
        } else {
            response.setFavorite(false);
        }
        return response;
    }

    private UserSummaryDto fetchAndBuildOwnerSummary(UUID ownerId) {
        try {
            // Предполагаем, что userServiceClient.getPublicUserProfile возвращает DTO,
            // из которого можно взять нужные поля.
            // PublicUserProfileResponse userProfileResponse = userServiceClient.getPublicUserProfile(ownerId); // Замени на реальный вызов
            // Mock
            PublicUserProfileResponse userProfileResponse = PublicUserProfileResponse.builder()
                    .userId(ownerId)
                    .username("testuser_" + ownerId.toString().substring(0,4))
                    .firstName("Имя")
                    .avatarUrl("http://example.com/avatar.jpg")
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
            log.error("Failed to fetch owner ({}) details from User Service: {}", ownerId, e.getMessage());
            // Возвращаем заглушку или null, чтобы не ломать основной ответ
        }
        return UserSummaryDto.builder().userId(ownerId).username("Пользователь " + ownerId.toString().substring(0,4)).build(); // Заглушка
    }
    // Метод для обогащения события ListingCreatedEvent данными о владельце и категории
    private ListingCreatedEvent enrichListingCreatedEvent(Listing listing) {
        UserSummaryDto ownerInfo = fetchAndBuildOwnerSummary(listing.getUserId());
        String categoryName = listing.getCategory() != null ? listing.getCategory().getName() : "N/A";

        return new ListingCreatedEvent(
                listing.getId(),
                listing.getUserId(),
                listing.getTitle(),
                listing.getDescription(),
                listing.getCategory() != null ? listing.getCategory().getId() : null,
                listing.getMainImageUrl(),
                listing.getAdditionalImageUrls(),
                listing.getLocationText(),
                listing.getPrice(),
                listing.getCurrency(),
                listing.getPriceType(),
                listing.getCreatedAt()
        );
    }
    // Метод для обогащения события ListingUpdatedEvent данными о категории
    private ListingUpdatedEvent enrichListingUpdatedEvent(Listing listing) {
        String categoryName = listing.getCategory() != null ? listing.getCategory().getName() : "N/A";
        return new ListingUpdatedEvent(
                listing.getId(),
                listing.getTitle(),
                listing.getDescription(),
                listing.getCategory() != null ? listing.getCategory().getId() : null,
                listing.getMainImageUrl(),
                listing.getAdditionalImageUrls(),
                listing.getLocationText(),
                listing.getPrice(),
                listing.getCurrency(),
                listing.getPriceType()
        );
    }

}
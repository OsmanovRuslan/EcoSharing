package ru.ecosharing.listing_service.elasticsearch.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.Criteria;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;
import org.springframework.data.elasticsearch.core.query.Query;
import org.springframework.data.elasticsearch.core.query.UpdateQuery;
import org.springframework.stereotype.Service;
import ru.ecosharing.listing_service.elasticsearch.document.ListingDocument;
import ru.ecosharing.listing_service.elasticsearch.repository.ListingSearchRepository;
import ru.ecosharing.listing_service.dto.kafka.*;
import ru.ecosharing.listing_service.elasticsearch.service.ElasticsearchIndexService;
import ru.ecosharing.listing_service.enums.AvailabilityStatus;
import ru.ecosharing.listing_service.enums.ModerationStatus;
import ru.ecosharing.listing_service.model.Category; // Нужна для получения categoryName
import ru.ecosharing.listing_service.model.Listing;   // Нужна для получения данных пользователя
import ru.ecosharing.listing_service.repository.CategoryRepository;
import ru.ecosharing.listing_service.repository.ListingRepository; // Для получения полных данных


import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ElasticsearchIndexServiceImpl implements ElasticsearchIndexService {

    private final ListingSearchRepository listingSearchRepository;
    private final ListingRepository listingPostgresRepository; // Для получения полных данных при создании/обновлении
    private final CategoryRepository categoryRepository;     // Для получения categoryName
    private final ElasticsearchOperations elasticsearchOperations; // Для сложных обновлений

    @Override
    public void processListingCreatedEvent(ListingCreatedEvent event) {
        log.info("Processing ListingCreatedEvent for listingId: {}", event.getListingId());
        try {
            // Обогащаем данными, которых нет в событии, но нужны для ES документа
            String categoryName = categoryRepository.findById(event.getCategoryId())
                    .map(Category::getName)
                    .orElse("N/A");

            // Здесь можно добавить логику получения ownerUsername и ownerAvatarUrl,
            // если они не приходят в событии и мы хотим их индексировать.
            // Это может потребовать запроса к User Service, что сделает обработку события дольше.
            // Альтернатива: ListingService при создании объявления получает эти данные и включает их в событие.
            // Пока оставим их пустыми или null.

            ListingDocument document = ListingDocument.builder()
                    .id(event.getListingId().toString())
                    .title(event.getTitle())
                    .description(event.getDescription())
                    .categoryId(event.getCategoryId())
                    .categoryName(categoryName) // Денормализованное имя
                    .locationText(event.getLocationText())
                    .price(event.getPrice())
                    .currency(event.getCurrency())
                    .priceType(event.getPriceType())
                    .moderationStatus(ModerationStatus.PENDING_MODERATION) // Из события или дефолт
                    .availabilityStatus(AvailabilityStatus.AVAILABLE) // Из события или дефолт
                    .createdAt(event.getCreatedAt())
                    .viewCount(0)
                    .ownerUserId(event.getUserId())
                    .mainImageUrl(event.getMainImageUrl())
                    .additionalImageUrls(event.getAdditionalImageUrls() != null ? event.getAdditionalImageUrls() : Collections.emptyList())
                    .build();
            listingSearchRepository.save(document);
            log.info("Listing {} indexed in Elasticsearch.", event.getListingId());
        } catch (Exception e) {
            log.error("Error indexing new listing {}: {}", event.getListingId(), e.getMessage(), e);
            // Здесь можно добавить логику повторной попытки или отправки в DLQ
        }
    }

    @Override
    public void processListingUpdatedEvent(ListingUpdatedEvent event) {
        log.info("Processing ListingUpdatedEvent for listingId: {}", event.getListingId());
        Optional<ListingDocument> existingDocOpt = listingSearchRepository.findById(event.getListingId().toString());

        if (existingDocOpt.isEmpty()) {
            log.warn("ListingDocument with id {} not found in Elasticsearch for update. Attempting to re-index.", event.getListingId());
            // Попытка полной переиндексации, если документ почему-то отсутствует
            listingPostgresRepository.findById(event.getListingId()).ifPresent(this::reindexListing);
            return;
        }

        ListingDocument docToUpdate = existingDocOpt.get();
        boolean changed = false;

        // Обновляем поля, если они пришли в событии
        if (event.getTitle() != null) { docToUpdate.setTitle(event.getTitle()); changed = true; }
        if (event.getDescription() != null) { docToUpdate.setDescription(event.getDescription()); changed = true; }
        if (event.getLocationText() != null) { docToUpdate.setLocationText(event.getLocationText()); changed = true; }
        if (event.getPrice() != null) { docToUpdate.setPrice(event.getPrice()); changed = true; }
        if (event.getCurrency() != null) { docToUpdate.setCurrency(event.getCurrency()); changed = true; }
        if (event.getPriceType() != null) { docToUpdate.setPriceType(event.getPriceType()); changed = true; }
        if (event.getMainImageUrl() != null) { docToUpdate.setMainImageUrl(event.getMainImageUrl()); changed = true; }
        if (event.getAdditionalImageUrls() != null) { docToUpdate.setAdditionalImageUrls(event.getAdditionalImageUrls()); changed = true; }

        if (event.getCategoryId() != null && !event.getCategoryId().equals(docToUpdate.getCategoryId())) {
            docToUpdate.setCategoryId(event.getCategoryId());
            String categoryName = categoryRepository.findById(event.getCategoryId()).map(Category::getName).orElse("N/A");
            docToUpdate.setCategoryName(categoryName);
            changed = true;
        }

        if (changed) {
            try {
                listingSearchRepository.save(docToUpdate);
                log.info("Listing {} updated in Elasticsearch.", event.getListingId());
            } catch (Exception e) {
                log.error("Error updating listing {} in Elasticsearch: {}", event.getListingId(), e.getMessage(), e);
            }
        } else {
            log.info("No relevant fields changed for listing {} in Elasticsearch update event.", event.getListingId());
        }
    }

    @Override
    public void processListingModerationStatusChangedEvent(ListingModerationStatusChangedEvent event) {
        log.info("Processing ListingModerationStatusChangedEvent for listingId: {}, newStatus: {}", event.getListingId(), event.getNewModerationStatus());
        updateFieldInElasticsearch(event.getListingId().toString(), "moderationStatus", event.getNewModerationStatus().name());
        updateFieldInElasticsearch(event.getListingId().toString(), "updatedAt", LocalDateTime.now());
    }

    @Override
    public void processListingAvailabilityStatusChangedEvent(ListingAvailabilityStatusChangedEvent event) {
        log.info("Processing ListingAvailabilityStatusChangedEvent for listingId: {}, newStatus: {}", event.getListingId(), event.getNewAvailabilityStatus());
        updateFieldInElasticsearch(event.getListingId().toString(), "availabilityStatus", event.getNewAvailabilityStatus().name());
        updateFieldInElasticsearch(event.getListingId().toString(), "updatedAt", LocalDateTime.now());
    }

    @Override
    public void processListingViewCountIncrementedEvent(ListingViewCountIncrementedEvent event) {
        log.debug("Processing ListingViewCountIncrementedEvent for listingId: {}, newCount: {}", event.getListingId(), event.getNewViewCount());
        // Это может быть слишком частым обновлением. Рассмотреть батчинг или обновление по скрипту.
        updateFieldInElasticsearch(event.getListingId().toString(), "viewCount", event.getNewViewCount());
    }

    @Override
    public void processListingDeletedEvent(ListingDeletedEvent event) {
        log.info("Processing ListingDeletedEvent for listingId: {}", event.getListingId());
        try {
            listingSearchRepository.deleteById(event.getListingId().toString());
            log.info("Listing {} deleted from Elasticsearch.", event.getListingId());
        } catch (Exception e) {
            log.error("Error deleting listing {} from Elasticsearch: {}", event.getListingId(), e.getMessage(), e);
        }
    }

    @Override
    public void processCategoryLifecycleEvent(CategoryLifecycleEvent event) {
        log.info("Processing CategoryLifecycleEvent for categoryId: {}, type: {}, isActive: {}",
                event.getCategoryId(), event.getEventType(), event.isActive());

        if ("CATEGORY_DEACTIVATED".equals(event.getEventType()) || "CATEGORY_UPDATED".equals(event.getEventType())) {
            // Если категория деактивирована или ее имя изменилось,
            // нужно найти все объявления этой категории в ES и обновить их (например, categoryName или добавить флаг).
            // Это может быть ресурсоемко, если объявлений много.
            // Для CATEGORY_DEACTIVATED: возможно, просто обновить moderationStatus на NEEDS_REVISION в ES тоже.
            // Для CATEGORY_UPDATED (если изменилось имя): обновить categoryName.

            String newCategoryName = event.isActive() ?
                    categoryRepository.findById(event.getCategoryId()).map(Category::getName).orElse("N/A") :
                    "Категория неактивна"; // Или старое имя, если не хотим менять на "неактивна"

            // Поиск всех документов по categoryId
            Query query = new CriteriaQuery(new Criteria("categoryId").is(event.getCategoryId()));
            SearchHits<ListingDocument> hits = elasticsearchOperations.search(query, ListingDocument.class);

            if (hits.getTotalHits() > 0) {
                List<UpdateQuery> updateQueries = hits.getSearchHits().stream()
                        .map(SearchHit::getContent)
                        .map(doc -> {
                            Map<String, Object> params = new HashMap<>();
                            params.put("categoryName", newCategoryName);
                            if ("CATEGORY_DEACTIVATED".equals(event.getEventType()) && doc.getModerationStatus() == ModerationStatus.ACTIVE) {
                                params.put("moderationStatus", ModerationStatus.NEEDS_REVISION.name());
                            }
                            params.put("updatedAt", LocalDateTime.now());
                            return UpdateQuery.builder(doc.getId())
                                    .withParams(params) // Используем params для обновления через скрипт или document
                                    .build();
                        })
                        .collect(Collectors.toList());

                if (!updateQueries.isEmpty()) {
                    elasticsearchOperations.bulkUpdate(updateQueries, elasticsearchOperations.getIndexCoordinatesFor(ListingDocument.class));
                    log.info("Обновлено {} объявлений в Elasticsearch из-за изменения категории {}", updateQueries.size(), event.getCategoryId());
                }
            }
        }
        // Для CATEGORY_ACTIVATED обычно не требуется массовых действий с объявлениями в ES,
        // т.к. они уже должны быть в NEEDS_REVISION, если были затронуты деактивацией.
    }

    // Вспомогательный метод для частичного обновления поля
    private void updateFieldInElasticsearch(String docId, String fieldName, Object value) {
        try {
            UpdateQuery updateQuery = UpdateQuery.builder(docId)
                    .withParams(Map.of(fieldName, value))
                    .build();
            elasticsearchOperations.update(updateQuery, elasticsearchOperations.getIndexCoordinatesFor(ListingDocument.class));
            log.debug("Field '{}' updated to '{}' for ES document ID: {}", fieldName, value, docId);
        } catch (Exception e) {
            log.error("Error updating field '{}' for ES document ID {}: {}", fieldName, docId, e.getMessage(), e);
        }
    }

    // Метод для полной переиндексации одного объявления (если нужно)
    private void reindexListing(Listing listing) {
        if (listing == null) return;
        log.info("Re-indexing listing {}", listing.getId());

        String categoryName = listing.getCategory() != null ? listing.getCategory().getName() : "N/A";
        // Допустим, данные о пользователе мы не можем легко получить здесь синхронно
        // или они уже были в ListingCreatedEvent при первоначальной индексации
        // и не меняются (userId, username). Аватар может меняться.

        ListingDocument document = ListingDocument.builder()
                .id(listing.getId().toString())
                .title(listing.getTitle())
                .description(listing.getDescription())
                .categoryId(listing.getCategory().getId())
                .categoryName(categoryName)
                .locationText(listing.getLocationText())
                .price(listing.getPrice())
                .currency(listing.getCurrency())
                .priceType(listing.getPriceType())
                .moderationStatus(listing.getModerationStatus())
                .availabilityStatus(listing.getAvailabilityStatus())
                .createdAt(listing.getCreatedAt())
                .viewCount(listing.getViewCount())
                .ownerUserId(listing.getUserId())
                // ownerUsername и ownerAvatarUrl нужно будет либо брать из старого документа ES,
                // либо из события, либо смириться, что при полной переиндексации они могут не обновиться без вызова User Service
                .mainImageUrl(listing.getMainImageUrl())
                .additionalImageUrls(listing.getAdditionalImageUrls() != null ? listing.getAdditionalImageUrls() : Collections.emptyList())
                .build();
        listingSearchRepository.save(document);
    }
}
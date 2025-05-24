package ru.ecosharing.listing_service.elasticsearch.service;

import ru.ecosharing.listing_service.dto.kafka.*; // Импорт всех наших событий

public interface ElasticsearchIndexService {

    void processListingCreatedEvent(ListingCreatedEvent event);

    void processListingUpdatedEvent(ListingUpdatedEvent event);

    void processListingModerationStatusChangedEvent(ListingModerationStatusChangedEvent event);

    void processListingAvailabilityStatusChangedEvent(ListingAvailabilityStatusChangedEvent event);

    void processListingViewCountIncrementedEvent(ListingViewCountIncrementedEvent event);

    void processListingDeletedEvent(ListingDeletedEvent event);

    void processCategoryLifecycleEvent(CategoryLifecycleEvent event); // Для обновления categoryName в объявлениях
}
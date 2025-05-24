package ru.ecosharing.listing_service.kafka.producer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;
import ru.ecosharing.listing_service.dto.kafka.*; // Импорт всех наших DTO событий
import ru.ecosharing.listing_service.enums.AvailabilityStatus;
import ru.ecosharing.listing_service.enums.ModerationStatus;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class ListingEventProducer {

    private final KafkaTemplate<String, AbstractListingEvent> listingEventKafkaTemplate;
    // private final KafkaTemplate<String, CategoryLifecycleEvent> categoryEventKafkaTemplate; // Если отдельный шаблон для категорий

    @Value("${kafka.topic.listing-events:listing-events}") // Топик для событий объявлений
    private String listingEventsTopic;

    @Value("${kafka.topic.category-events:category-events}") // Топик для событий категорий
    private String categoryEventsTopic;


    // Методы для каждого типа события Listing
    public void sendListingCreatedEvent(ListingCreatedEvent event) {
        sendListingEvent(event.getListingId().toString(), event, "создания объявления");
    }

    public void sendListingUpdatedEvent(ListingUpdatedEvent event) {
        sendListingEvent(event.getListingId().toString(), event, "обновления объявления");
    }

    public void sendListingModerationStatusChangedEvent(ListingModerationStatusChangedEvent event) {
        sendListingEvent(event.getListingId().toString(), event, "изменения статуса модерации");
    }

    // Перегруженный метод для удобства вызова из CategoryService и ModerationService
    public void sendListingModerationStatusChangedEvent(UUID listingId, ModerationStatus newStatus, ModerationStatus oldStatus, UUID moderatorId) {
        ListingModerationStatusChangedEvent event = new ListingModerationStatusChangedEvent(listingId, newStatus, oldStatus, moderatorId);
        sendListingEvent(listingId.toString(), event, "изменения статуса модерации");
    }


    public void sendListingAvailabilityStatusChangedEvent(ListingAvailabilityStatusChangedEvent event) {
        sendListingEvent(event.getListingId().toString(), event, "изменения статуса доступности");
    }

    // Перегруженный метод
    public void sendListingAvailabilityStatusChangedEvent(UUID listingId, AvailabilityStatus newStatus) {
        ListingAvailabilityStatusChangedEvent event = new ListingAvailabilityStatusChangedEvent(listingId, newStatus);
        sendListingEvent(listingId.toString(), event, "изменения статуса доступности");
    }

    public void sendListingViewCountIncrementedEvent(ListingViewCountIncrementedEvent event) {
        // Логирование для этого события может быть слишком частым, возможно, только DEBUG
        log.debug("Attempting to send event for listing view count increment: {}", event.getListingId());
        sendListingEvent(event.getListingId().toString(), event, "инкремента просмотров", true);
    }

    public void sendListingDeletedEvent(ListingDeletedEvent event) {
        sendListingEvent(event.getListingId().toString(), event, "удаления объявления");
    }


    // Общий метод для отправки событий Listing
    private void sendListingEvent(String key, AbstractListingEvent event, String eventDescriptionForLog) {
        sendListingEvent(key, event, eventDescriptionForLog, false);
    }

    private void sendListingEvent(String key, AbstractListingEvent event, String eventDescriptionForLog, boolean debugOnlyLog) {
        try {
            if (!debugOnlyLog) {
                log.info("Отправка события {} (ID: {}) в Kafka топик '{}'. Ключ: {}",
                        event.getEventType(), event.getEventId(), listingEventsTopic, key);
            } else {
                log.debug("Отправка события {} (ID: {}) в Kafka топик '{}'. Ключ: {}",
                        event.getEventType(), event.getEventId(), listingEventsTopic, key);
            }

            CompletableFuture<SendResult<String, AbstractListingEvent>> future =
                    listingEventKafkaTemplate.send(listingEventsTopic, key, event);

            future.whenComplete((result, ex) -> {
                if (ex == null) {
                    if (!debugOnlyLog) {
                        log.info("Событие {} (ID: {}) успешно отправлено в Kafka. Topic: {}, Partition: {}, Offset: {}",
                                event.getEventType(), event.getEventId(),
                                result.getRecordMetadata().topic(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    } else {
                        log.debug("Событие {} (ID: {}) успешно отправлено в Kafka. Topic: {}, Partition: {}, Offset: {}",
                                event.getEventType(), event.getEventId(),
                                result.getRecordMetadata().topic(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                } else {
                    log.error("Ошибка отправки события {} (ID: {}) в Kafka: {}",
                            event.getEventType(), event.getEventId(), ex.getMessage(), ex);
                    // Здесь можно добавить логику для DLQ или других механизмов обработки ошибок
                }
            });
        } catch (Exception e) {
            log.error("Критическая ошибка при попытке отправки события {} для {} в Kafka: {}",
                    event.getEventType(), eventDescriptionForLog, e.getMessage(), e);
        }
    }


    // Метод для событий категорий
    public void sendCategoryLifecycleEvent(CategoryLifecycleEvent event) {
        try {
            log.info("Отправка события {} (ID: {}) для категории {} в Kafka топик '{}'. Ключ: {}",
                    event.getEventType(), event.getEventId(), event.getCategoryId(), categoryEventsTopic, event.getCategoryId().toString());

            // Если используем отдельный KafkaTemplate для категорий:
            // CompletableFuture<SendResult<String, CategoryLifecycleEvent>> future =
            // categoryEventKafkaTemplate.send(categoryEventsTopic, event.getCategoryId().toString(), event);

            // Если используем общий KafkaTemplate<String, Object> и соответствующий ProducerFactory:
            // KafkaTemplate<String, Object> genericKafkaTemplate = ...;
            // CompletableFuture<SendResult<String, Object>> future =
            // genericKafkaTemplate.send(categoryEventsTopic, event.getCategoryId().toString(), event);

            // Если используем тот же KafkaTemplate<String, AbstractListingEvent>, то он не подойдет для CategoryLifecycleEvent
            // напрямую, если CategoryLifecycleEvent не наследуется от AbstractListingEvent.
            // Для простоты, если топик один, а типы разные, лучше иметь KafkaTemplate<String, Object>
            // или настроить JsonSerializer для работы с разными типами в одном ProducerFactory.
            // Либо, как сделано в KafkaProducerConfig, иметь отдельную фабрику и шаблон для категорий.

            // Предположим, у нас есть KafkaTemplate<String, Object> или мы кастуем (не рекомендуется без проверки)
            // Для примера, будем считать, что у нас есть отдельный KafkaTemplate для категорий,
            // либо мы настроили общий ProducerFactory, который может работать с Object и JsonSerializer
            // правильно определит тип по заголовкам (если JsonSerializer.ADD_TYPE_INFO_HEADERS=true).
            // Пока что закомментируем реальную отправку, т.к. KafkaTemplate был для AbstractListingEvent.
            // Это место требует уточнения конфигурации KafkaProducerConfig.

            // ЗАГЛУШКА: Реальная отправка события категории потребует соответствующего KafkaTemplate
            log.warn("Отправка CategoryLifecycleEvent требует настроенного KafkaTemplate для типа CategoryLifecycleEvent или Object.");

            /*
            CompletableFuture<SendResult<String, CategoryLifecycleEvent>> future =
                    categoryEventKafkaTemplate.send(categoryEventsTopic, event.getCategoryId().toString(), event); // Если есть categoryEventKafkaTemplate

            future.whenComplete((result, ex) -> {
                if (ex == null) {
                    log.info("Событие {} (ID: {}) для категории {} успешно отправлено в Kafka.",
                            event.getEventType(), event.getEventId(), event.getCategoryId());
                } else {
                    log.error("Ошибка отправки события {} (ID: {}) для категории {} в Kafka: {}",
                            event.getEventType(), event.getEventId(), event.getCategoryId(), ex.getMessage(), ex);
                }
            });
            */

        } catch (Exception e) {
            log.error("Критическая ошибка при попытке отправки события {} для категории {} в Kafka: {}",
                    event.getEventType(), event.getCategoryId(), e.getMessage(), e);
        }
    }
}
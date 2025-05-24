package ru.ecosharing.listing_service.kafka.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.TopicPartition;
import org.springframework.kafka.listener.AbstractConsumerSeekAware;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import ru.ecosharing.listing_service.elasticsearch.service.ElasticsearchIndexService;
import ru.ecosharing.listing_service.dto.kafka.*; // Импорт всех DTO событий

@Slf4j
@Component
@RequiredArgsConstructor
public class ListingEventConsumer extends AbstractConsumerSeekAware {

    private final ElasticsearchIndexService elasticsearchIndexService;

    // Слушатель для всех событий объявлений
    @KafkaListener(
            topics = "${kafka.topic.listing-events:listing-events}",
            groupId = "${spring.kafka.consumer.group-id.listing-events:listing-service-indexer-group}",
            containerFactory = "listingEventKafkaListenerContainerFactory" // Используем фабрику для AbstractListingEvent
    )
    public void consumeListingEvent(@Payload(required = false) AbstractListingEvent event, ConsumerRecord<String, AbstractListingEvent> record) {
        if (event == null) {
            log.error("Получено null событие из топика {}. Запись: {}. Вероятно, ошибка десериализации.", record.topic(), record);
            // Можно отправить в DLQ или просто проигнорировать
            return;
        }

        log.info("Получено событие из Kafka: Type='{}', ListingID='{}', EventID='{}', Topic='{}', Partition={}, Offset={}",
                event.getEventType(), event.getListingId(), event.getEventId(),
                record.topic(), record.partition(), record.offset());

        try {
            // Определяем тип события и вызываем соответствующий метод сервиса индексации
            // Это можно сделать через instanceof или по полю event.getEventType()
            if (event instanceof ListingCreatedEvent) {
                elasticsearchIndexService.processListingCreatedEvent((ListingCreatedEvent) event);
            } else if (event instanceof ListingUpdatedEvent) {
                elasticsearchIndexService.processListingUpdatedEvent((ListingUpdatedEvent) event);
            } else if (event instanceof ListingModerationStatusChangedEvent) {
                elasticsearchIndexService.processListingModerationStatusChangedEvent((ListingModerationStatusChangedEvent) event);
            } else if (event instanceof ListingAvailabilityStatusChangedEvent) {
                elasticsearchIndexService.processListingAvailabilityStatusChangedEvent((ListingAvailabilityStatusChangedEvent) event);
            } else if (event instanceof ListingViewCountIncrementedEvent) {
                elasticsearchIndexService.processListingViewCountIncrementedEvent((ListingViewCountIncrementedEvent) event);
            } else if (event instanceof ListingDeletedEvent) {
                elasticsearchIndexService.processListingDeletedEvent((ListingDeletedEvent) event);
            } else {
                log.warn("Получено неизвестное или необрабатываемое событие типа '{}' для ListingID '{}'",
                        event.getEventType(), event.getListingId());
            }
        } catch (Exception e) {
            log.error("Ошибка при обработке Kafka события Type='{}', ListingID='{}': {}",
                    event.getEventType(), event.getListingId(), e.getMessage(), e);
            // Здесь важна стратегия обработки ошибок:
            // 1. Повторные попытки (если настроен DefaultErrorHandler с Retry)
            // 2. Отправка в Dead Letter Topic (DLT)
            // 3. Пропуск сообщения (риск потери данных в ES)
            // По умолчанию наш DefaultErrorHandler не делает повторов и не пробрасывает исключение,
            // чтобы не останавливать консьюмер. Но для DLQ нужно будет его настроить.
            // throw new RuntimeException("Failed to process listing event", e); // Если хотим, чтобы ErrorHandler сработал
        }
    }

    // Отдельный слушатель для событий категорий
    @KafkaListener(
            topics = "${kafka.topic.category-events:category-events}",
            groupId = "${spring.kafka.consumer.group-id.category-events:listing-service-category-indexer-group}", // Отдельная группа для событий категорий
            containerFactory = "categoryEventKafkaListenerContainerFactory" // НУЖНА ОТДЕЛЬНАЯ ФАБРИКА для CategoryLifecycleEvent
            // или общая фабрика для Object
    )
    public void consumeCategoryEvent(@Payload(required = false) CategoryLifecycleEvent event, ConsumerRecord<String, CategoryLifecycleEvent> record) {
        if (event == null) {
            log.error("Получено null событие из топика {}. Запись: {}. Вероятно, ошибка десериализации.", record.topic(), record);
            return;
        }

        log.info("Получено событие категории из Kafka: Type='{}', CategoryID='{}', EventID='{}'",
                event.getEventType(), event.getCategoryId(), event.getEventId());
        try {
            elasticsearchIndexService.processCategoryLifecycleEvent(event);
        } catch (Exception e) {
            log.error("Ошибка при обработке Kafka события категории Type='{}', CategoryID='{}': {}",
                    event.getEventType(), event.getCategoryId(), e.getMessage(), e);
        }
    }
}
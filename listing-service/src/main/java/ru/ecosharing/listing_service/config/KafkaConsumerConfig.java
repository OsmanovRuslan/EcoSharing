package ru.ecosharing.listing_service.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler; // Уже должен быть импортирован
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.util.backoff.FixedBackOff; // Уже должен быть импортирован
import ru.ecosharing.listing_service.dto.kafka.AbstractListingEvent;
import ru.ecosharing.listing_service.dto.kafka.CategoryLifecycleEvent;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@EnableKafka
@Configuration
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    // Group ID для событий объявлений
    @Value("${spring.kafka.consumer.group-id.listing-events:listing-service-indexer-group}")
    private String listingEventsGroupId;

    // === НОВОЕ: Group ID для событий категорий ===
    @Value("${spring.kafka.consumer.group-id.category-events:listing-service-category-event-group}") // Можно другое имя для группы
    private String categoryEventsGroupId;
    // ==========================================

    @Value("${spring.kafka.consumer.properties.spring.json.trusted.packages:ru.ecosharing.*,java.util,java.lang}")
    private String trustedPackages;

    // --- Существующая фабрика для AbstractListingEvent ---
    @Bean
    public ConsumerFactory<String, AbstractListingEvent> listingEventConsumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, listingEventsGroupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class.getName());
        props.put(JsonDeserializer.TRUSTED_PACKAGES, trustedPackages);
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, AbstractListingEvent.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        log.info("Настройка Kafka ConsumerFactory для AbstractListingEvent: group={}, trustedPackages={}",
                listingEventsGroupId, trustedPackages);
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean("listingEventKafkaListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, AbstractListingEvent> listingEventKafkaListenerContainerFactory(
            ConsumerFactory<String, AbstractListingEvent> listingEventConsumerFactory, // Инжектим правильную фабрику
            CommonErrorHandler kafkaErrorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, AbstractListingEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(listingEventConsumerFactory);
        factory.setCommonErrorHandler(kafkaErrorHandler);
        return factory;
    }

    // === НОВЫЕ БИНЫ для CategoryLifecycleEvent ===
    @Bean
    public ConsumerFactory<String, CategoryLifecycleEvent> categoryEventConsumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, categoryEventsGroupId); // Используем отдельный group ID
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class.getName());
        props.put(JsonDeserializer.TRUSTED_PACKAGES, trustedPackages); // Убедитесь, что пакет CategoryLifecycleEvent здесь есть или покрывается ru.ecosharing.*
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, CategoryLifecycleEvent.class.getName());
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false); // Обычно false, если тип указан в VALUE_DEFAULT_TYPE
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        log.info("Настройка Kafka ConsumerFactory для CategoryLifecycleEvent: group={}, trustedPackages={}",
                categoryEventsGroupId, trustedPackages);
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean("categoryEventKafkaListenerContainerFactory") // Имя бина, которое используется в @KafkaListener
    public ConcurrentKafkaListenerContainerFactory<String, CategoryLifecycleEvent> categoryEventKafkaListenerContainerFactory(
            ConsumerFactory<String, CategoryLifecycleEvent> categoryEventConsumerFactory, // Инжектим фабрику для категорий
            CommonErrorHandler kafkaErrorHandler) { // Можно использовать тот же ErrorHandler
        ConcurrentKafkaListenerContainerFactory<String, CategoryLifecycleEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(categoryEventConsumerFactory);
        factory.setCommonErrorHandler(kafkaErrorHandler);
        // Другие настройки фабрики, если нужны (concurrency и т.д.)
        return factory;
    }
    // ===================================================

    // Общий обработчик ошибок (остается без изменений)
    @Bean
    public CommonErrorHandler kafkaErrorHandler() {
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                (record, exception) -> {
                    log.error("--- Ошибка обработки Kafka сообщения ---");
                    log.error("Topic: {}, Partition: {}, Offset: {}, Key: {}",
                            record.topic(), record.partition(), record.offset(), record.key());
                    log.error("Exception: {}", exception.getMessage(), exception);
                    log.error("--- Конец ошибки Kafka ---");
                },
                new FixedBackOff(0L, 0L)
        );
        errorHandler.setAckAfterHandle(false);
        return errorHandler;
    }
}
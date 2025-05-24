package ru.ecosharing.listing_service.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;
import ru.ecosharing.listing_service.dto.kafka.AbstractListingEvent;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Configuration
public class KafkaProducerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.producer.acks:all}")
    private String acks;

    // Общая фабрика для всех событий объявлений, если они имеют общего предка или интерфейс
    @Bean
    public ProducerFactory<String, AbstractListingEvent> listingEventProducerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        configProps.put(ProducerConfig.ACKS_CONFIG, acks);
        // Дополнительные настройки для production (idempotence, retries, linger.ms, batch.size)
        configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        configProps.put(ProducerConfig.RETRIES_CONFIG, 3);
        configProps.put(ProducerConfig.LINGER_MS_CONFIG, 5); // Небольшая задержка для батчинга

        // Важно для JsonSerializer, чтобы он не добавлял информацию о типе в заголовки,
        // если консьюмер не ожидает этого или использует свою логику определения типа.
        // Для событий с общим предком это может быть полезно оставить true (по умолчанию),
        // если консьюмер настроен на работу с заголовками типов.
        // configProps.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);

        log.info("Настройка Kafka ProducerFactory для AbstractListingEvent: servers={}, acks={}", bootstrapServers, acks);
        return new DefaultKafkaProducerFactory<>(configProps);
    }

    @Bean
    public KafkaTemplate<String, AbstractListingEvent> listingEventKafkaTemplate() {
        return new KafkaTemplate<>(listingEventProducerFactory());
    }

    // Если будут события для категорий, можно сделать отдельную фабрику и KafkaTemplate
    // или использовать Object в KafkaTemplate и настраивать сериализатор более гибко.
    // Например, для CategoryLifecycleEvent:
    /*
    @Bean
    public ProducerFactory<String, CategoryLifecycleEvent> categoryEventProducerFactory() {
        // ... похожая конфигурация ...
        return new DefaultKafkaProducerFactory<>(configProps);
    }

    @Bean
    public KafkaTemplate<String, CategoryLifecycleEvent> categoryEventKafkaTemplate() {
        return new KafkaTemplate<>(categoryEventProducerFactory());
    }
    */
}
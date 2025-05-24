package ru.ecosharing.auth_service.config; // Укажи правильный пакет

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
// Важно: Используем DTO из Auth Service для отправки
import ru.ecosharing.auth_service.dto.NotificationRequestKafkaDto; // DTO для отправки

import java.util.HashMap;
import java.util.Map;

/**
 * Конфигурация Kafka Producer для отправки запросов в Notification Service.
 */
@Slf4j
@Configuration
public class KafkaProducerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.producer.acks:1}")
    private String acks;
    @Value("${spring.kafka.producer.properties.linger.ms:5}")
    private Integer lingerMs;

    /**
     * Фабрика для Kafka Producer'ов, отправляющих NotificationRequestKafkaDto.
     */
    @Bean
    public ProducerFactory<String, NotificationRequestKafkaDto> notificationProducerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        configProps.put(ProducerConfig.ACKS_CONFIG, acks);
        if (lingerMs != null) {
            configProps.put(ProducerConfig.LINGER_MS_CONFIG, lingerMs);
        }
        log.info("Настройка Kafka ProducerFactory для NotificationRequestKafkaDto: servers={}, acks={}, lingerMs={}",
                bootstrapServers, acks, lingerMs);
        return new DefaultKafkaProducerFactory<>(configProps);
    }

    /**
     * Бин KafkaTemplate для отправки NotificationRequestKafkaDto.
     */
    @Bean
    public KafkaTemplate<String, NotificationRequestKafkaDto> notificationKafkaTemplate(
            ProducerFactory<String, NotificationRequestKafkaDto> notificationProducerFactory) {
        log.info("Настройка KafkaTemplate для NotificationRequestKafkaDto завершена.");
        return new KafkaTemplate<>(notificationProducerFactory);
    }
}
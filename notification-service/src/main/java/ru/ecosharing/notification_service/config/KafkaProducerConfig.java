package ru.ecosharing.notification_service.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer; // Используем JsonSerializer
import ru.ecosharing.notification_service.dto.kafka.TelegramSendMessageKafkaDto; // DTO для отправки в бот

import java.util.HashMap;
import java.util.Map;

/**
 * Конфигурация Kafka Producer для отправки запросов на отправку Telegram сообщений
 * в топик 'telegram-send-requests'.
 */
@Slf4j
@Configuration
public class KafkaProducerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.producer.acks:all}")
    private String acks;

    @Value("${spring.kafka.producer.properties.linger.ms:5}")
    private Integer lingerMs;

    /**
     * Фабрика для создания Kafka Producer'ов для отправки сообщений в Telegram Bot Service.
     * Настраивает серверы, сериализаторы (String для ключа, JSON для значения).
     * @return Сконфигурированная ProducerFactory.
     */
    @Bean
    public ProducerFactory<String, TelegramSendMessageKafkaDto> telegramProducerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        configProps.put(ProducerConfig.ACKS_CONFIG, acks);
        if (lingerMs != null) {
            configProps.put(ProducerConfig.LINGER_MS_CONFIG, lingerMs);
        }
        configProps.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);

        log.info("Настройка Kafka ProducerFactory для TelegramSendMessageKafkaDto: servers={}, acks={}, lingerMs={}",
                bootstrapServers, acks, lingerMs);

        return new DefaultKafkaProducerFactory<>(configProps);
    }

    /**
     * Бин KafkaTemplate для удобной отправки сообщений типа TelegramSendMessageKafkaDto.
     * Использует настроенную ProducerFactory.
     * @param telegramProducerFactory Фабрика продюсеров для Telegram сообщений.
     * @return Сконфигурированный KafkaTemplate.
     */
    @Bean
    public KafkaTemplate<String, TelegramSendMessageKafkaDto> telegramKafkaTemplate(
            ProducerFactory<String, TelegramSendMessageKafkaDto> telegramProducerFactory) {
        log.info("Настройка KafkaTemplate для TelegramSendMessageKafkaDto завершена.");
        return new KafkaTemplate<>(telegramProducerFactory);
    }
}
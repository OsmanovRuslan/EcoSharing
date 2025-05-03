package ru.ecosharing.notification_service.config;

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
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.util.backoff.FixedBackOff;
import ru.ecosharing.notification_service.dto.kafka.NotificationRequestKafkaDto;

import java.util.HashMap;
import java.util.Map;

/**
 * Конфигурация Kafka Consumer для чтения запросов на уведомления из топика 'notification-requests'.
 */
@Slf4j
@EnableKafka
@Configuration
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;

    // Доверенные пакеты из application.yml
    @Value("${spring.kafka.consumer.properties.spring.json.trusted.packages}")
    private String trustedPackages;

    /**
     * Создает фабрику Kafka Consumer'ов.
     * Настраивает серверы Kafka, group ID, десериализаторы (String для ключа, JSON с ErrorHandling для значения).
     * @return ConsumerFactory<String, NotificationRequestKafkaDto>
     */
    @Bean
    public ConsumerFactory<String, NotificationRequestKafkaDto> notificationConsumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class.getName());
        props.put("spring.json.fail.on.unknown.properties", false);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, trustedPackages);
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, NotificationRequestKafkaDto.class.getName());
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, "false");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        log.info("Настройка Kafka ConsumerFactory для NotificationRequestKafkaDto: servers={}, groupId={}, trustedPackages={}",
                bootstrapServers, groupId, trustedPackages);

        return new DefaultKafkaConsumerFactory<>(props);
    }

    /**
     * Создает обработчик ошибок для Kafka Listener'ов.
     * По умолчанию логирует ошибку и не выполняет повторных попыток для данного сообщения.
     * @return CommonErrorHandler
     */
    @Bean
    public CommonErrorHandler kafkaErrorHandler() {
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                (record, exception) -> {
                    log.error("--- Kafka Listener Error Recovery ---");
                    log.error("Не удалось обработать сообщение из Kafka после всех попыток (или без них).");
                    log.error("Topic: {}", record.topic());
                    log.error("Partition: {}", record.partition());
                    log.error("Offset: {}", record.offset());
                    log.error("Key: {}", record.key());
                    log.error("Exception: {}", exception.getMessage(), exception);
                    log.error("--- End Kafka Error Recovery ---");
                },
                new FixedBackOff(0L, 0L)
        );
        log.info("Настройка DefaultErrorHandler для Kafka: без повторных попыток.");
        return errorHandler;
    }

    /**
     * Создает фабрику контейнеров для Kafka Listener'ов.
     * Эта фабрика будет использоваться Spring для создания listener'ов,
     * аннотированных @KafkaListener(containerFactory = "notificationKafkaListenerContainerFactory").
     * @param notificationConsumerFactory Фабрика Consumer'ов для запросов уведомлений.
     * @param kafkaErrorHandler Общий обработчик ошибок.
     * @return ConcurrentKafkaListenerContainerFactory<String, NotificationRequestKafkaDto>
     */
    @Bean("notificationKafkaListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, NotificationRequestKafkaDto> notificationKafkaListenerContainerFactory(
            ConsumerFactory<String, NotificationRequestKafkaDto> notificationConsumerFactory,
            CommonErrorHandler kafkaErrorHandler) {

        ConcurrentKafkaListenerContainerFactory<String, NotificationRequestKafkaDto> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(notificationConsumerFactory);
        factory.setCommonErrorHandler(kafkaErrorHandler);
        log.info("Настройка ConcurrentKafkaListenerContainerFactory для NotificationRequestKafkaDto завершена.");
        return factory;
    }
}
package ru.ecosharing.telegram_webhook_gateway.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

import java.util.Map;

/**
 * Конфигурация для работы с Kafka.
 * Создает топик для обновлений Telegram.
 * ProducerFactory и KafkaTemplate будут созданы автоконфигурацией Spring Boot
 * на основе настроек в application.yml.
 */
@Configuration
public class KafkaConfig {

    @Value("${telegram.kafka.topic}")
    private String topicName;

    // Эти значения используются для создания топика, если его нет.
    // Они не влияют на поведение продюсера напрямую (оно берется из application.yml)
    @Value("${kafka.topic.partitions:3}")
    private int partitions;

    @Value("${kafka.topic.replicas:3}")
    private int replicas;

    @Value("${kafka.topic.minInSyncReplicas:2}")
    private int minInSyncReplicas;


    /**
     * Создает топик Kafka для обновлений Telegram, если он еще не существует.
     * Использует настройки из application properties или их дефолтные значения.
     *
     * @return Конфигурация топика Kafka
     */
    @Bean
    NewTopic createTopic() {
        return TopicBuilder.name(topicName)
                .partitions(partitions)
                .replicas(replicas)
                .configs(Map.of("min.insync.replicas", String.valueOf(minInSyncReplicas)))
                .build();
    }
}
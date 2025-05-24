package ru.ecosharing.telegram_bot_service.config;

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
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer; // Используем ErrorHandlingDeserializer
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.ecosharing.telegram_bot_service.dto.TelegramSendMessageKafkaDto;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@EnableKafka
@Configuration
public class KafkaConsumerConfig {

    @Value("${spring.kafka.consumer.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;

    // Доверенные пакеты для десериализации JSON
    @Value("${spring.kafka.consumer.properties.spring.json.trusted.packages}")
    private String trustedPackages;


    @Bean
    public ConsumerFactory<String, Update> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        // Ключ - строка
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        // Значение - используем ErrorHandlingDeserializer
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        // Делегируем реальную десериализацию JsonDeserializer
        props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class.getName());
        // Передаем свойства для JsonDeserializer
        props.put(JsonDeserializer.TRUSTED_PACKAGES, trustedPackages); // Используем значение из properties
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, Update.class.getName()); // Указываем тип по умолчанию
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, "false"); // Не используем заголовки типов Kafka

        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        return new DefaultKafkaConsumerFactory<>(props); // Не указываем десериализаторы явно, они в props
    }

    @Bean
    public CommonErrorHandler errorHandler() {
        return new DefaultErrorHandler((record, exception) -> {
            log.error("Kafka listener error processing record: {}. Exception: {}", record, exception.getMessage(), exception);
            // Не пробрасываем исключение дальше, чтобы consumer не остановился (зависит от стратегии)
        });
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Update> updateKafkaListenerContainerFactory(
            ConsumerFactory<String, Update> consumerFactory,
            CommonErrorHandler errorHandler) { // Инжектим зависимости
        ConcurrentKafkaListenerContainerFactory<String, Update> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(errorHandler); // Устанавливаем обработчик ошибок
        return factory;
    }

    @Bean
    public ConsumerFactory<String, TelegramSendMessageKafkaDto> telegramSendCommandConsumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId); // Может быть та же группа
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class.getName());
        props.put(JsonDeserializer.TRUSTED_PACKAGES, trustedPackages + ",ru.ecosharing.telegram_bot_service.dto.kafka");
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, TelegramSendMessageKafkaDto.class.getName());
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, "false");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, TelegramSendMessageKafkaDto> telegramSendCommandKafkaListenerContainerFactory(
            ConsumerFactory<String, TelegramSendMessageKafkaDto> telegramSendCommandConsumerFactory,
            CommonErrorHandler errorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, TelegramSendMessageKafkaDto> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(telegramSendCommandConsumerFactory);
        factory.setCommonErrorHandler(errorHandler);
        return factory;
    }
}
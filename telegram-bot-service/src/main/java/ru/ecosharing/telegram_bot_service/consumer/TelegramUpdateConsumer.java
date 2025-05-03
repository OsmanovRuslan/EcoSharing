package ru.ecosharing.telegram_bot_service.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.ecosharing.telegram_bot_service.bot.EcoSharingBot;

/**
 * Kafka Consumer для получения обновлений Telegram из топика.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TelegramUpdateConsumer {

    private final EcoSharingBot ecoSharingBot;

    @KafkaListener(topics = "${kafka.topic}", groupId = "${spring.kafka.consumer.group-id}")
    public void consume(
            @Payload(required = false) Update update,
            ConsumerRecord<String, Update> record
    ) {

        // Проверяем, не было ли ошибки десериализации (ErrorHandlingDeserializer вернет null)
        if (update == null) {
            log.error("Received null update from Kafka (deserialization error?). Record: {}", record);
            // acknowledgment.acknowledge(); // Подтверждаем смещение, чтобы не пытаться обработать снова
            return;
        }


        log.info("Received update ID: {} from Kafka. Key: {}, Partition: {}, Offset: {}",
                update.getUpdateId(), record.key(), record.partition(), record.offset());

        try {
            BotApiMethod<?> responseMethod = ecoSharingBot.onWebhookUpdateReceived(update);

            if (responseMethod != null) {
                log.debug("Handler returned a response method: {}", responseMethod.getClass().getSimpleName());
                ecoSharingBot.executeMethodAsync(responseMethod);
            } else {
                log.debug("Handler did not return a response method for update ID: {}", update.getUpdateId());
            }

            // acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("Error processing Telegram update ID: {} from Kafka record: {}",
                    update.getUpdateId(), record, e);
            // Обработка ошибки - не подтверждаем offset или отправляем в DLQ
        }
    }
}
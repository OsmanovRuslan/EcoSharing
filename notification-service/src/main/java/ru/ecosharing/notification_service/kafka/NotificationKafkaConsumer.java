package ru.ecosharing.notification_service.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord; // Для доступа к метаданным сообщения Kafka
import org.springframework.kafka.annotation.KafkaListener; // Основная аннотация для Kafka Consumer
import org.springframework.messaging.handler.annotation.Payload; // Для извлечения тела сообщения
import org.springframework.stereotype.Component;
import ru.ecosharing.notification_service.dto.kafka.NotificationRequestKafkaDto; // DTO сообщения из Kafka
import ru.ecosharing.notification_service.service.NotificationService; // Сервис для обработки уведомления

/**
 * Kafka Consumer для получения запросов на уведомления из топика 'notification-requests'.
 * Десериализует сообщения в NotificationRequestKafkaDto и передает их
 * в NotificationService для дальнейшей обработки и отправки.
 */
@Slf4j // Логгер Lombok
@Component // Объявляем класс как Spring компонент
@RequiredArgsConstructor // Генерирует конструктор для внедрения зависимостей
public class NotificationKafkaConsumer {

    private final NotificationService notificationService; // Сервис, который будет обрабатывать запрос

    /**
     * Метод-слушатель для Kafka топика, указанного в 'kafka.topic.notification-requests'.
     * Использует containerFactory, настроенный в KafkaConsumerConfig.
     *
     * @param requestDto Десериализованный объект NotificationRequestKafkaDto.
     *                   Может быть null, если ErrorHandlingDeserializer не смог десериализовать
     *                   сообщение и заменил его на null.
     * @param record     Полный объект ConsumerRecord, содержащий метаданные сообщения
     *                   (topic, partition, offset, key, headers и т.д.).
     */
    // Аннотация @KafkaListener настраивает этот метод как обработчик сообщений из Kafka
    @KafkaListener(
            topics = "${kafka.topic.notification-requests}", // Имя топика из application.yml
            groupId = "${spring.kafka.consumer.group-id}",    // ID группы консьюмеров из application.yml
            containerFactory = "notificationKafkaListenerContainerFactory" // Имя бина фабрики контейнеров из KafkaConsumerConfig
    )
    public void consumeNotificationRequest(
            @Payload(required = false) NotificationRequestKafkaDto requestDto,
            ConsumerRecord<String, NotificationRequestKafkaDto> record // Для доступа к метаданным Kafka
    ) {
        // 1. Проверка на null (если ErrorHandlingDeserializer вернул null из-за ошибки десериализации)
        if (requestDto == null) {
            log.error("Получен null объект NotificationRequestKafkaDto из Kafka (вероятно, ошибка десериализации). " +
                            "Topic: {}, Partition: {}, Offset: {}, Key: {}",
                    record.topic(), record.partition(), record.offset(), record.key());
            return;
        }

        // 2. Логирование полученного сообщения
        log.info("Получен запрос на уведомление из Kafka. " +
                        "Topic: {}, Partition: {}, Offset: {}, Key: {}. " +
                        "UserId: {}, Type: {}",
                record.topic(), record.partition(), record.offset(), record.key(),
                requestDto.getUserId(), requestDto.getNotificationType());
        log.debug("Полные данные запроса из Kafka: {}", requestDto);

        // 3. Передача запроса в NotificationService для обработки
        try {
            notificationService.processNotificationRequest(requestDto);
            log.debug("Запрос на уведомление для UserId {} успешно передан в NotificationService.", requestDto.getUserId());
        } catch (Exception e) {
            log.error("Критическая ошибка при передаче Kafka сообщения в NotificationService для UserId {}: Тип {}, Сообщение: {}",
                    requestDto.getUserId(), requestDto.getNotificationType(), e.getMessage(), e);
             throw new RuntimeException("Ошибка обработки Kafka сообщения сервисом уведомлений", e);
        }
    }
}
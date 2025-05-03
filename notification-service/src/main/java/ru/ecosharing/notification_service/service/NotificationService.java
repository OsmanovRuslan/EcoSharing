package ru.ecosharing.notification_service.service;

import ru.ecosharing.notification_service.dto.kafka.NotificationRequestKafkaDto;

/**
 * Интерфейс основного сервиса уведомлений.
 * Определяет контракт для обработки запросов на отправку уведомлений.
 */
public interface NotificationService {

    /**
     * Обрабатывает входящий запрос на уведомление, полученный из Kafka.
     * Этот метод является точкой входа для всей логики отправки:
     * получение данных пользователя, выбор каналов, форматирование,
     * сохранение In-App уведомления, отправка через Notifier'ы и SSE.
     *
     * @param request DTO с данными уведомления, полученный из Kafka.
     */
    void processNotificationRequest(NotificationRequestKafkaDto request);

}
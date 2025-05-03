package ru.ecosharing.notification_service.service;

import org.springframework.http.codec.ServerSentEvent; // Объект SSE
import reactor.core.publisher.Flux; // Реактивный поток
import ru.ecosharing.notification_service.dto.UserNotificationDto; // DTO для отправки клиенту

import java.util.UUID;

/**
 * Интерфейс сервиса для управления SSE (Server-Sent Events) потоками уведомлений.
 * Отвечает за подписку клиентов и отправку им событий.
 */
public interface SseService {

    /**
     * Подписывает пользователя на получение уведомлений через SSE.
     * Создает или возвращает существующий Flux<ServerSentEvent> для данного пользователя.
     * Управляет жизненным циклом соединения (heartbeat, закрытие при отписке/ошибке).
     *
     * @param userId ID пользователя, который подписывается.
     * @return Flux<ServerSentEvent<UserNotificationDto>> - бесконечный поток событий SSE для клиента.
     */
    Flux<ServerSentEvent<UserNotificationDto>> subscribe(UUID userId);

    /**
     * Отправляет уведомление (как событие SSE) конкретному пользователю,
     * если у него есть активная SSE подписка.
     *
     * @param userId ID пользователя-получателя.
     * @param notificationDto DTO уведомления для отправки в поле 'data' события SSE.
     */
    void sendNotificationToUser(UUID userId, UserNotificationDto notificationDto);

    /**
     * Принудительно удаляет подписчика и закрывает связанный с ним SSE поток.
     * Может вызываться, например, при logout пользователя или при ошибках.
     *
     * @param userId ID пользователя, которого нужно отписать.
     */
    void removeSubscriber(UUID userId);
}
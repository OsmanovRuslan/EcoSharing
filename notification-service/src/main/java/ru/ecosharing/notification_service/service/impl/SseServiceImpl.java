package ru.ecosharing.notification_service.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.util.concurrent.Queues;
import ru.ecosharing.notification_service.dto.UserNotificationDto;
import ru.ecosharing.notification_service.service.SseService;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Реализация сервиса для управления SSE потоками.
 * Использует Project Reactor Sinks для управления подписчиками и отправки событий.
 */
@Slf4j
@Service
public class SseServiceImpl implements SseService {

    // Потокобезопасная карта для хранения Sinks (источников событий) для каждого активного подписчика SSE
    // Ключ: userId, Значение: Sink, через который отправляются события этому пользователю
    private final Map<UUID, Sinks.Many<ServerSentEvent<UserNotificationDto>>> userSinks = new ConcurrentHashMap<>();

    // Размер буфера для событий, если клиент не успевает их читать
    private static final int SINK_BUFFER_SIZE = Queues.SMALL_BUFFER_SIZE; // Обычно 256
    // Интервал для отправки keep-alive пакетов (в секундах)
    private static final long KEEP_ALIVE_INTERVAL_SECONDS = 20;

    /**
     * Подписывает пользователя на получение уведомлений через SSE.
     * @param userId ID пользователя.
     * @return Flux событий для этого пользователя.
     */
    @Override
    public Flux<ServerSentEvent<UserNotificationDto>> subscribe(UUID userId) {
        // Потокобезопасно получаем или создаем Sink для данного пользователя
        Sinks.Many<ServerSentEvent<UserNotificationDto>> sink = userSinks.computeIfAbsent(userId, id -> {
            log.info("Создание нового SSE Sink для пользователя ID: {}", id);
            // Sinks.many().multicast() - позволяет иметь несколько подписчиков на один Sink
            //                            (например, если пользователь открыл несколько вкладок)
            // onBackpressureBuffer() - если клиент медленный, события буферизуются.
            //                          Второй параметр (false) - не отменять подписку при переполнении.
            return Sinks.many().multicast().onBackpressureBuffer(SINK_BUFFER_SIZE, false);
        });

        log.info("Пользователь ID {} подписался/переподключился к SSE.", userId);

        // Создаем Flux (поток) из Sink'а, который будет отправлен клиенту
        return sink.asFlux()
                // Объединяем с потоком keep-alive сообщений
                .mergeWith(Flux.interval(Duration.ofSeconds(KEEP_ALIVE_INTERVAL_SECONDS))
                        .map(i -> ServerSentEvent.<UserNotificationDto>builder()
                                .comment("sse-keep-alive") // Комментарий, чтобы не обрабатывать на клиенте
                                .build()))
                // Действия при отмене подписки (например, закрытие вкладки)
                .doOnCancel(() -> {
                    log.info("SSE подписка отменена (doOnCancel) для пользователя ID {}", userId);
                    removeSink(userId, sink, "cancel");
                })
                // Действия при возникновении ошибки в потоке
                .doOnError(error -> {
                    log.warn("Ошибка в SSE потоке для пользователя ID {}: {}", userId, error.getMessage());
                    removeSink(userId, sink, "error");
                })
                // Действия при завершении потока (нормальном или из-за ошибки)
                .doOnTerminate(() -> {
                    log.info("SSE поток завершен (doOnTerminate) для пользователя ID {}", userId);
                    removeSink(userId, sink, "terminate");
                });
    }

    /**
     * Отправляет уведомление конкретному пользователю через его активный SSE поток.
     * @param userId ID пользователя-получателя.
     * @param notificationDto DTO уведомления для отправки.
     */
    @Override
    public void sendNotificationToUser(UUID userId, UserNotificationDto notificationDto) {
        // Получаем Sink для пользователя из карты
        Sinks.Many<ServerSentEvent<UserNotificationDto>> sink = userSinks.get(userId);

        // Если Sink существует (т.е. пользователь подключен)
        if (sink != null) {
            log.debug("Отправка SSE события 'new_notification' пользователю ID {}", userId);
            // Создаем Server-Sent Event
            ServerSentEvent<UserNotificationDto> event = ServerSentEvent.<UserNotificationDto>builder()
                    .id(notificationDto.getId().toString()) // ID события (ID уведомления)
                    .event("new_notification") // Тип события для клиента
                    .data(notificationDto) // Данные события
                    .build();

            // Пытаемся отправить событие в Sink (неблокирующая операция)
            Sinks.EmitResult result = sink.tryEmitNext(event);

            // Обрабатываем результат отправки
            if (result.isFailure()) {
                log.warn("Не удалось отправить SSE событие пользователю ID {}. Причина: {}", userId, result);
                // Если нет подписчиков (все вкладки закрыты), удаляем Sink
                if (result == Sinks.EmitResult.FAIL_ZERO_SUBSCRIBER) {
                    log.info("Нет активных SSE подписчиков для пользователя ID {}, удаляем Sink.", userId);
                    removeSink(userId, sink, "zero_subscribers");
                }
                // Если буфер переполнен (клиент не успевает читать)
                else if (result == Sinks.EmitResult.FAIL_OVERFLOW) {
                    log.warn("Буфер SSE переполнен для пользователя ID {}. Событие потеряно.", userId);
                }
                // Другие ошибки (терминирован, отменен) также могут требовать удаления Sink'а
                else if (result == Sinks.EmitResult.FAIL_CANCELLED || result == Sinks.EmitResult.FAIL_TERMINATED) {
                    removeSink(userId, sink, result.name());
                }
            } else {
                log.trace("SSE событие для пользователя ID {} успешно добавлено в Sink.", userId);
            }
        } else {
            // Пользователь не подключен по SSE в данный момент
            log.trace("Нет активного SSE соединения для пользователя ID {} для отправки уведомления.", userId);
        }
    }

    /**
     * Принудительно удаляет подписчика (Sink) из карты.
     * @param userId ID пользователя.
     */
    @Override
    public void removeSubscriber(UUID userId) {
        Sinks.Many<ServerSentEvent<UserNotificationDto>> sink = userSinks.remove(userId);
        if (sink != null) {
            // Завершаем Sink, чтобы уведомить всех его подписчиков
            sink.tryEmitComplete();
            log.info("SSE Sink принудительно удален и завершен для пользователя ID {}", userId);
        }
    }

    /**
     * Потокобезопасный метод удаления Sink'а из карты.
     * Удаляет Sink, только если он совпадает с переданным (для предотвращения гонок).
     */
    private void removeSink(UUID userId, Sinks.Many<ServerSentEvent<UserNotificationDto>> sinkToRemove, String reason) {
        boolean removed = userSinks.remove(userId, sinkToRemove);
        if (removed) {
            log.info("Удален SSE Sink для пользователя {} по причине: {}", userId, reason);
        } else {
            log.debug("Попытка удалить уже отсутствующий или замененный Sink для пользователя {}", userId);
        }
    }
}
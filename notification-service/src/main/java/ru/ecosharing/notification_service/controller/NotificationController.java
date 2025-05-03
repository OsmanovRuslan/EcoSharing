package ru.ecosharing.notification_service.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import ru.ecosharing.notification_service.dto.MarkReadRequestDto;
import ru.ecosharing.notification_service.dto.UnreadCountDto;
import ru.ecosharing.notification_service.dto.UserNotificationDto;
import ru.ecosharing.notification_service.service.NotificationQueryService;
import ru.ecosharing.notification_service.service.SseService;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST контроллер для взаимодействия с фронтендом по поводу уведомлений пользователя.
 * Предоставляет SSE поток для обновлений в реальном времени и REST API
 * для получения истории уведомлений и управления их статусом прочтения.
 * Все эндпоинты требуют аутентификации пользователя (проверяется через @PreAuthorize).
 */
@Slf4j
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationQueryService notificationQueryService; // Сервис для чтения данных и управления статусом
    private final SseService sseService; // Сервис для управления SSE потоками

    // --- Эндпоинты для получения уведомлений ---

    /**
     * Получает страницу уведомлений для ТЕКУЩЕГО аутентифицированного пользователя.
     * Позволяет фильтровать по статусу прочтения (параметр ?unread=true/false).
     * Поддерживает стандартную пагинацию Spring Data (параметры ?page, ?size, ?sort).
     *
     * @param unread   Опциональный параметр для фильтрации (true=непрочитанные, false=прочитанные, null=все).
     * @param pageable Параметры пагинации и сортировки (по умолчанию: size=20, sort=createdAt,desc).
     * @return ResponseEntity со статусом 200 OK и страницей (Page) UserNotificationDto.
     */
    @GetMapping("/my")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Page<UserNotificationDto>> getMyNotifications(
            @RequestParam(required = false) Boolean unread,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        UUID currentUserId = getCurrentUserIdOrThrow(); // Получаем ID текущего пользователя
        log.info("GET /api/notifications/my - User ID: {}, Unread filter: {}, Pageable: {}", currentUserId, unread, pageable);

        // Вызываем сервис для получения данных
        Page<UserNotificationDto> notificationPage = notificationQueryService.getUserNotifications(currentUserId, unread, pageable);
        return ResponseEntity.ok(notificationPage); // Возвращаем страницу и статус 200
    }

    /**
     * Получает количество непрочитанных уведомлений для ТЕКУЩЕГО аутентифицированного пользователя.
     *
     * @return ResponseEntity со статусом 200 OK и DTO UnreadCountDto.
     */
    @GetMapping("/my/unread-count")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UnreadCountDto> getUnreadCount() {
        UUID currentUserId = getCurrentUserIdOrThrow();
        log.debug("GET /api/notifications/my/unread-count - User ID: {}", currentUserId);
        long count = notificationQueryService.getUnreadNotificationCount(currentUserId);
        return ResponseEntity.ok(new UnreadCountDto(count)); // Возвращаем количество и статус 200
    }

    // --- Эндпоинты для управления статусом прочтения ---

    /**
     * Помечает одно конкретное уведомление как прочитанное для ТЕКУЩЕГО пользователя.
     *
     * @param notificationId Уникальный ID уведомления, которое нужно пометить.
     * @return ResponseEntity со статусом 204 No Content при успехе.
     *         Возвращает 404 Not Found, если уведомление не найдено или не принадлежит пользователю.
     */
    @PostMapping("/{notificationId}/read") // Используем POST, т.к. операция изменяет состояние ресурса
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> markAsRead(@PathVariable UUID notificationId) {
        UUID currentUserId = getCurrentUserIdOrThrow();
        log.info("POST /api/notifications/{}/read - Пометка прочитанным для User ID: {}", notificationId, currentUserId);
        try {
            notificationQueryService.markNotificationsAsRead(currentUserId, List.of(notificationId));
            return ResponseEntity.noContent().build(); // Статус 204 при успехе
        } catch (Exception e) {
            // Ловим возможные исключения из сервиса (например, если уведомление не найдено - хотя сервис может и не бросать)
            log.error("Ошибка при пометке уведомления {} прочитанным для пользователя {}: {}", notificationId, currentUserId, e.getMessage());
            // Можно вернуть более конкретную ошибку, если сервис их предоставляет
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Помечает список уведомлений как прочитанные для ТЕКУЩЕГО пользователя.
     *
     * @param request DTO, содержащий список notificationIds.
     * @return ResponseEntity со статусом 204 No Content при успехе.
     *         Возвращает 400 Bad Request, если список ID пуст.
     */
    @PostMapping("/my/mark-read")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> markListAsRead(@Valid @RequestBody MarkReadRequestDto request) {
        UUID currentUserId = getCurrentUserIdOrThrow();
        // Проверка на пустой список уже есть в DTO (@NotEmpty)
        log.info("POST /api/notifications/my/mark-read - Пометка списка уведомлений ({}) прочитанными для User ID: {}",
                request.getNotificationIds().size(), currentUserId);
        try {
            notificationQueryService.markNotificationsAsRead(currentUserId, request.getNotificationIds());
            return ResponseEntity.noContent().build(); // Статус 204
        } catch (Exception e) {
            log.error("Ошибка при пометке списка уведомлений прочитанными для пользователя {}: {}", currentUserId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Помечает ВСЕ непрочитанные уведомления ТЕКУЩЕГО пользователя как прочитанные.
     *
     * @return ResponseEntity со статусом 200 OK и телом, содержащим количество помеченных уведомлений.
     */
    @PostMapping("/my/mark-all-read")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Long>> markAllAsRead() {
        UUID currentUserId = getCurrentUserIdOrThrow();
        log.info("POST /api/notifications/my/mark-all-read - Пометка всех прочитанными для User ID: {}", currentUserId);
        try {
            long markedCount = notificationQueryService.markAllNotificationsAsRead(currentUserId);
            return ResponseEntity.ok(Map.of("markedCount", markedCount)); // Возвращаем кол-во и статус 200
        } catch (Exception e) {
            log.error("Ошибка при пометке всех уведомлений прочитанными для пользователя {}: {}", currentUserId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // --- Эндпоинт для Server-Sent Events (SSE) ---

    /**
     * Устанавливает SSE соединение для ТЕКУЩЕГО аутентифицированного пользователя.
     * Клиент подключается к этому эндпоинту, и сервер будет отправлять события
     * 'new_notification' при появлении новых уведомлений для этого пользователя.
     *
     * @return Flux<ServerSentEvent<UserNotificationDto>> - Поток SSE событий.
     */
    @GetMapping(path = "/my/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("isAuthenticated()")
    public Flux<ServerSentEvent<UserNotificationDto>> streamNotifications() {
        UUID currentUserId;
        try {
            currentUserId = getCurrentUserIdOrThrow(); // Получаем ID
            log.info("SSE соединение запрошено для пользователя ID: {}", currentUserId);
            // Делегируем создание потока SseService
            return sseService.subscribe(currentUserId);
        } catch (AuthenticationCredentialsNotFoundException e) {
            // Если не удалось получить ID пользователя (маловероятно с @PreAuthorize)
            log.error("Не удалось установить SSE соединение: {}", e.getMessage());
            // Возвращаем поток с ошибкой
            return Flux.error(e);
        } catch (Exception e) {
            log.error("Неожиданная ошибка при установке SSE соединения: {}", e.getMessage(), e);
            return Flux.error(e);
        }
    }

    // --- Вспомогательный метод для получения ID текущего пользователя ---
    /**
     * Извлекает UUID текущего аутентифицированного пользователя из SecurityContext.
     * @return UUID пользователя.
     * @throws AuthenticationCredentialsNotFoundException если пользователь не аутентифицирован
     *         или не удалось извлечь UUID из деталей аутентификации.
     */
    private UUID getCurrentUserIdOrThrow() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || !(authentication.getDetails() instanceof UUID)) {
            String principalName = (authentication != null) ? authentication.getName() : "null";
            log.error("Не удалось извлечь UUID пользователя из SecurityContext. Principal: {}, Details: {}",
                    principalName, authentication != null ? authentication.getDetails() : "null");
            throw new AuthenticationCredentialsNotFoundException("Необходима аутентификация с валидным JWT токеном, содержащим UUID пользователя в деталях.");
        }
        return (UUID) authentication.getDetails();
    }

}
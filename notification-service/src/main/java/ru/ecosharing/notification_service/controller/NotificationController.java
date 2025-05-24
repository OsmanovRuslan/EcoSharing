package ru.ecosharing.notification_service.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
// import org.springframework.http.codec.ServerSentEvent; // <<< УДАЛИТЬ
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
// import reactor.core.publisher.Flux; // <<< УДАЛИТЬ
import ru.ecosharing.notification_service.dto.MarkReadRequestDto;
import ru.ecosharing.notification_service.dto.UnreadCountDto;
import ru.ecosharing.notification_service.dto.UserNotificationDto;
import ru.ecosharing.notification_service.service.NotificationQueryService;
// import ru.ecosharing.notification_service.service.SseService; // <<< УДАЛИТЬ

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationQueryService notificationQueryService;
    // private final SseService sseService; // <<< УДАЛИТЬ

    @GetMapping("/my")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Page<UserNotificationDto>> getMyNotifications(
            @RequestParam(required = false) Boolean unread,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        UUID currentUserId = getCurrentUserIdOrThrow();
        log.info("GET /api/notifications/my - User ID: {}, Unread filter: {}, Pageable: {}", currentUserId, unread, pageable);
        Page<UserNotificationDto> notificationPage = notificationQueryService.getUserNotifications(currentUserId, unread, pageable);
        return ResponseEntity.ok(notificationPage);
    }

    @GetMapping("/my/unread-count")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UnreadCountDto> getUnreadCount() {
        UUID currentUserId = getCurrentUserIdOrThrow();
        log.debug("GET /api/notifications/my/unread-count - User ID: {}", currentUserId);
        long count = notificationQueryService.getUnreadNotificationCount(currentUserId);
        return ResponseEntity.ok(new UnreadCountDto(count));
    }

    @PostMapping("/{notificationId}/read")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> markAsRead(@PathVariable UUID notificationId) {
        UUID currentUserId = getCurrentUserIdOrThrow();
        log.info("POST /api/notifications/{}/read - User ID: {}", notificationId, currentUserId);
        try {
            notificationQueryService.markNotificationsAsRead(currentUserId, List.of(notificationId));
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            log.error("Ошибка при пометке уведомления {} прочитанным для пользователя {}: {}", notificationId, currentUserId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/my/mark-read")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> markListAsRead(@Valid @RequestBody MarkReadRequestDto request) {
        UUID currentUserId = getCurrentUserIdOrThrow();
        log.info("POST /api/notifications/my/mark-read - User ID: {}, Count: {}", currentUserId, request.getNotificationIds().size());
        try {
            notificationQueryService.markNotificationsAsRead(currentUserId, request.getNotificationIds());
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            log.error("Ошибка при пометке списка уведомлений прочитанными для пользователя {}: {}", currentUserId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/my/mark-all-read")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Long>> markAllAsRead() {
        UUID currentUserId = getCurrentUserIdOrThrow();
        log.info("POST /api/notifications/my/mark-all-read - User ID: {}", currentUserId);
        try {
            long markedCount = notificationQueryService.markAllNotificationsAsRead(currentUserId);
            return ResponseEntity.ok(Map.of("markedCount", markedCount));
        } catch (Exception e) {
            log.error("Ошибка при пометке всех уведомлений прочитанными для пользователя {}: {}", currentUserId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // --- Эндпоинт для SSE УДАЛЕН ---
    // @GetMapping(path = "/my/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    // ...

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
package ru.ecosharing.user_service.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.ecosharing.user_service.dto.request.AvailabilityCheckRequest;
import ru.ecosharing.user_service.dto.request.CreateUserProfileRequest;
import ru.ecosharing.user_service.dto.response.AvailabilityCheckResponse;
import ru.ecosharing.user_service.dto.response.UserCredentialsResponse;
import ru.ecosharing.user_service.dto.response.UserNotificationDetailsDto;
import ru.ecosharing.user_service.service.UserService;

import java.util.UUID;

/**
 * REST контроллер для внутренних запросов от других сервисов (например, Auth Service).
 * Эндпоинты должны быть защищены соответствующим образом в production.
 */
@Slf4j
@RestController
@RequestMapping("/api/internal") // Отдельный префикс для внутренних API
@RequiredArgsConstructor
public class UserInternalController {

    private final UserService userService;

    /**
     * Эндпоинт для создания профиля пользователя (вызывается Auth Service).
     */
    @PostMapping("/users")
    public ResponseEntity<Void> createUser(@Valid @RequestBody CreateUserProfileRequest request) {
        log.info("POST /api/internal/users - Запрос на создание профиля от Auth Service для userId: {}", request.getUserId());
        userService.createUserProfile(request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /**
     * Эндпоинт для получения учетных данных по логину (вызывается Auth Service).
     */
    @GetMapping("/credentials/by-login")
    public ResponseEntity<UserCredentialsResponse> getUserCredentialsByLogin(@RequestParam String login) {
        log.debug("GET /api/internal/credentials/by-login - login: {}", login);
        UserCredentialsResponse response = userService.getUserCredentialsByLogin(login);
        return ResponseEntity.ok(response);
    }

    /**
     * Эндпоинт для получения учетных данных по ID (вызывается Auth Service).
     */
    @GetMapping("/credentials/by-id")
    public ResponseEntity<UserCredentialsResponse> getUserCredentialsById(@RequestParam UUID userId) {
        log.debug("GET /api/internal/credentials/by-id - userId: {}", userId);
        UserCredentialsResponse response = userService.getUserCredentialsById(userId);
        return ResponseEntity.ok(response);
    }

    /**
     * Эндпоинт для проверки доступности username/email (вызывается Auth Service).
     */
    @PostMapping("/check-availability")
    public ResponseEntity<AvailabilityCheckResponse> checkAvailability(@Valid @RequestBody AvailabilityCheckRequest request) {
        log.debug("POST /api/internal/check-availability - username: {}, email: {}", request.getUsername(), request.getEmail());
        AvailabilityCheckResponse response = userService.checkAvailability(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Внутренний эндпоинт для получения данных пользователя, необходимых Notification Service.
     * Вызывается из Notification Service.
     * @param userId ID пользователя.
     * @return ResponseEntity с UserNotificationDetailsDto.
     */
    @GetMapping("/users/{userId}/notification-details")
    public ResponseEntity<UserNotificationDetailsDto> getUserNotificationDetailsInternal(@PathVariable UUID userId) {
        log.debug("GET /api/internal/users/{}/notification-details - Внутренний запрос данных для уведомлений", userId);
        UserNotificationDetailsDto responseDto = userService.getUserNotificationDetails(userId);
        return ResponseEntity.ok(responseDto);
    }
}
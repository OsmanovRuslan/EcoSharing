package ru.ecosharing.user_service.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import ru.ecosharing.user_service.dto.request.UpdateUserProfileRequest;
import ru.ecosharing.user_service.dto.request.UpdateUserSettingsRequest;
import ru.ecosharing.user_service.dto.response.PublicUserProfileResponse;
import ru.ecosharing.user_service.dto.response.UserProfileResponse;
import ru.ecosharing.user_service.dto.response.UserSettingsResponse;
import ru.ecosharing.user_service.security.JwtTokenProvider; // Для получения ID
import ru.ecosharing.user_service.service.UserService;

import java.util.UUID;

/**
 * REST контроллер для обработки запросов, связанных с пользователями.
 * Версия до добавления адресов.
 */
@Slf4j
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    // --- Эндпоинты для текущего пользователя ("/me") ---

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()") // Доступ только аутентифицированным
    public ResponseEntity<UserProfileResponse> getCurrentUserProfile() {
        UUID currentUserId = getCurrentUserIdOrThrow();
        log.info("GET /api/users/me - User ID: {}", currentUserId);
        UserProfileResponse profile = userService.getCurrentUserProfile(currentUserId);
        return ResponseEntity.ok(profile);
    }

    @PutMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UserProfileResponse> updateCurrentUserProfile(@Valid @RequestBody UpdateUserProfileRequest request) {
        UUID currentUserId = getCurrentUserIdOrThrow();
        log.info("PUT /api/users/me - User ID: {}", currentUserId);
        UserProfileResponse updatedProfile = userService.updateCurrentUserProfile(currentUserId, request);
        return ResponseEntity.ok(updatedProfile);
    }

    @GetMapping("/me/settings")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UserSettingsResponse> getCurrentUserSettings() {
        UUID currentUserId = getCurrentUserIdOrThrow();
        log.info("GET /api/users/me/settings - User ID: {}", currentUserId);
        UserSettingsResponse settings = userService.getCurrentUserSettings(currentUserId);
        return ResponseEntity.ok(settings);
    }

    @PutMapping("/me/settings")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UserSettingsResponse> updateCurrentUserSettings(@Valid @RequestBody UpdateUserSettingsRequest request) {
        UUID currentUserId = getCurrentUserIdOrThrow();
        log.info("PUT /api/users/me/settings - User ID: {}", currentUserId);
        UserSettingsResponse updatedSettings = userService.updateCurrentUserSettings(currentUserId, request);
        return ResponseEntity.ok(updatedSettings);
    }

    // --- Эндпоинт для получения публичного профиля другого пользователя ---

    @GetMapping("/{userId}/public")
    @PreAuthorize("isAuthenticated()") // Требуем аутентификации для просмотра
    public ResponseEntity<PublicUserProfileResponse> getPublicUserProfile(@PathVariable UUID userId) {
        log.info("GET /api/users/{}/public", userId);
        PublicUserProfileResponse publicProfile = userService.getPublicUserProfile(userId);
        return ResponseEntity.ok(publicProfile);
    }

    // --- Вспомогательный метод для получения ID текущего пользователя ---
    private UUID getCurrentUserIdOrThrow() {
        return JwtTokenProvider.getCurrentUserId()
                .orElseThrow(() -> {
                    log.error("Не удалось получить ID пользователя из контекста безопасности.");
                    return new IllegalStateException("Не удалось получить ID пользователя из контекста безопасности.");
                });
    }
}
package ru.ecosharing.user_service.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import ru.ecosharing.user_service.dto.request.*;
import ru.ecosharing.user_service.dto.response.*;
import ru.ecosharing.user_service.model.UserProfile;

import java.util.UUID;

/**
 * Интерфейс сервиса для управления данными пользователей.
 * (Версия с добавленным методом deleteUserAdmin)
 */
public interface UserService {

    // --- Операции для внутренних вызовов (Auth Service) ---
    void createUserProfile(CreateUserProfileRequest request);
    AvailabilityCheckResponse checkAvailability(AvailabilityCheckRequest request);
    UserCredentialsResponse getUserCredentialsByLogin(String login);
    UserCredentialsResponse getUserCredentialsById(UUID userId);

    // --- Операции для пользователя (Self-service) ---
    UserProfileResponse getCurrentUserProfile(UUID userId);
    UserProfileResponse updateCurrentUserProfile(UUID userId, UpdateUserProfileRequest request);
    UserSettingsResponse getCurrentUserSettings(UUID userId);
    UserSettingsResponse updateCurrentUserSettings(UUID userId, UpdateUserSettingsRequest request);

    // --- Операции для публичного доступа или других сервисов ---
    PublicUserProfileResponse getPublicUserProfile(UUID userId);

    // --- Операции для Администраторов ---
    Page<UserSummaryResponse> searchUsers(Specification<UserProfile> spec, Pageable pageable);
    UserProfileResponse getUserProfileAdmin(UUID userId);
    UserSettingsResponse getUserSettingsAdmin(UUID userId);
    UserProfileResponse updateUserProfileAdmin(UUID userId, AdminUpdateUserRequest request);

    UserNotificationDetailsDto getUserNotificationDetails(UUID userId);

    /**
     * Физически удаляет пользователя и связанные с ним данные (профиль, настройки).
     * ВНИМАНИЕ: Необратимая операция! Использовать с осторожностью.
     * Требует роли 'ADMIN'.
     * @param userId ID пользователя для удаления.
     * @throws ru.ecosharing.user_service.exception.ResourceNotFoundException если пользователь не найден.
     */
    void deleteUserAdmin(UUID userId); // <-- ДОБАВЛЕН МЕТОД
}
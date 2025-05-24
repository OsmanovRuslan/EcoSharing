package ru.ecosharing.user_service.mapper;

import ru.ecosharing.user_service.dto.request.CreateUserProfileRequest;
import ru.ecosharing.user_service.dto.response.*;
import ru.ecosharing.user_service.model.UserProfile;
import ru.ecosharing.user_service.model.UserSettings;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Утилитарный класс для маппинга между DTO и сущностями пользователя.
 */
public final class UserMapper {

    private UserMapper() {
    } // Приватный конструктор

    // --- Мапперы для UserProfile и UserSettings ---

    public static UserProfileResponse toUserProfileResponse(UserProfile userProfile) {
        if (userProfile == null) return null;
        return UserProfileResponse.builder()
                .userId(userProfile.getUserId())
                .username(userProfile.getUsername())
                .email(userProfile.getEmail())
                .firstName(userProfile.getFirstName())
                .lastName(userProfile.getLastName())
                .phone(userProfile.getPhone())
                .about(userProfile.getAbout())
                .avatarUrl(userProfile.getAvatarUrl())
                .rating(userProfile.getRating())
                .isActive(userProfile.isActive())
                .createdAt(userProfile.getCreatedAt())
                .updatedAt(userProfile.getUpdatedAt())
                // .addresses(...) // Удалено
                .build();
    }

    public static PublicUserProfileResponse toPublicUserProfileResponse(UserProfile userProfile) {
        if (userProfile == null) return null;
        return PublicUserProfileResponse.builder()
                .userId(userProfile.getUserId())
                .username(userProfile.getUsername())
                .avatarUrl(userProfile.getAvatarUrl())
                .rating(userProfile.getRating())
                .memberSince(userProfile.getCreatedAt())
                .build();
    }

    public static UserSettingsResponse toUserSettingsResponse(UserSettings userSettings) {
        if (userSettings == null) return null;
        return UserSettingsResponse.builder()
                .userId(userSettings.getUserId())
                .enableEmailNotifications(userSettings.isEnableEmailNotifications())
                .enableTelegramNotifications(userSettings.isEnableTelegramNotifications())
                .language(userSettings.getLanguage())
                .build();
    }

    public static UserCredentialsResponse toUserCredentialsResponse(UserProfile userProfile) {
        if (userProfile == null) return null;
        return new UserCredentialsResponse(
                userProfile.getUserId(),
                userProfile.getUsername(),
                userProfile.isActive()
        );
    }

    public static UserSummaryResponse toUserSummaryResponse(UserProfile userProfile) {
        if (userProfile == null) return null;
        return UserSummaryResponse.builder()
                .userId(userProfile.getUserId())
                .username(userProfile.getUsername())
                .fullName(userProfile.getFirstName() + " " + userProfile.getLastName())
                .email(userProfile.getEmail())
                .isActive(userProfile.isActive())
                .createdAt(userProfile.getCreatedAt())
                .build();
    }

    public static List<UserSummaryResponse> toUserSummaryResponseList(List<UserProfile> users) {
        if (users == null) return Collections.emptyList();
        return users.stream()
                .map(UserMapper::toUserSummaryResponse)
                .collect(Collectors.toList());
    }

    public static UserProfile toUserProfile(CreateUserProfileRequest request) {
        if (request == null) return null;
        UserProfile profile = UserProfile.builder()
                .userId(request.getUserId())
                .username(request.getUsername())
                .email(request.getEmail())
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .phone(request.getPhone())
                .isActive(true)
                .rating(new BigDecimal("0.0"))
                .build();

        // Создаем и связываем настройки по умолчанию
        UserSettings settings = UserSettings.builder()
                .enableEmailNotifications(true)
                .enableTelegramNotifications(true)
                .language("ru")
                .build();
        profile.setUserSettings(settings); // Устанавливаем связь
        return profile;
    }

    /**
     * Преобразует UserProfile и UserSettings в UserNotificationDetailsDto.
     * @param profile Сущность профиля.
     * @param settings Сущность настроек.
     * @return DTO с данными для уведомлений или null, если один из аргументов null.
     */
    public static UserNotificationDetailsDto toUserNotificationDetailsDto(UserProfile profile, UserSettings settings) {
        if (profile == null || settings == null) {
            return null;
        }
        return UserNotificationDetailsDto.builder()
                .userId(profile.getUserId())
                .email(profile.getEmail())
                .firstName(profile.getFirstName())
                .language(settings.getLanguage())
                .emailNotificationsEnabled(settings.isEnableEmailNotifications())
                .telegramNotificationsEnabled(settings.isEnableTelegramNotifications())
                .build();
    }
}
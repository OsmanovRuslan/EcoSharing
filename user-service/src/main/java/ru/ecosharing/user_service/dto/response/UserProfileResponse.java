package ru.ecosharing.user_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * DTO для представления полного профиля пользователя, включая настройки и адреса.
 * Обычно используется для ответа на запрос /api/users/me или для администратора.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileResponse {

    private UUID userId;
    private String username;
    private String email;
    private String firstName;
    private String lastName;
    private String fullName; // Комбинированное имя
    private String phone;
    private String about; // Поле "о себе"
    private String avatarUrl;
    private String location;
    private LocalDate birthDate;
    private boolean isActive;
    // Роли добавляются в сервисе/контроллере из SecurityContext (JWT)
    private List<String> roles;

    // Вложенные DTO для настроек и адресов
    private UserSettingsResponse settings;

    // Статистика
    private Integer ratingsCount;
    private BigDecimal rating;
    private Integer listingsCount;
    private Integer rentalsCount;

    // Аудит
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime lastLoginAt;
}
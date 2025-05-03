package ru.ecosharing.user_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO для представления публичной части профиля пользователя.
 * Содержит только ту информацию, которая может быть видна другим пользователям.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicUserProfileResponse {
    private UUID userId;
    // Отображаем либо username, либо полное имя, в зависимости от настроек приватности (здесь username)
    private String username;
    private String firstName; // Можно добавить имя, если оно публично
    // private String lastName; // Фамилия обычно не публична
    private String avatarUrl;
    private BigDecimal rating; // Средний рейтинг
    private Integer ratingsCount; // Количество оценок
    private String location; // Публичная локация (если разрешено)
    private String about; // Публичное описание (если разрешено)
    private LocalDateTime memberSince; // Дата регистрации (createdAt)
    // Не включаем email, phone, isActive, адреса, полные настройки, updatedAt, lastLoginAt
}

package ru.ecosharing.auth_service.model;

import lombok.Getter;

/**
 * Перечисление возможных ролей пользователей в системе.
 */
@Getter
public enum RoleName {
    ROLE_USER("ROLE_USER", "Обычный пользователь"),
    ROLE_ADMIN("ROLE_ADMIN", "Администратор"),
    ROLE_MODERATOR("ROLE_MODERATOR", "Модератор");

    private final String role; // Имя роли, используемое в Spring Security
    private final String description; // Описание роли

    RoleName(String role, String description) {
        this.role = role;
        this.description = description;
    }
}
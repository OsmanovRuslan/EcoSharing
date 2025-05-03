package ru.ecosharing.auth_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * DTO для ответа от User Service с данными, необходимыми Auth Service
 * для проверки статуса и получения username/userId.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserCredentialsResponse {
    private UUID userId; // ID пользователя
    private String username; // Имя пользователя (логин)
    private boolean isActive; // Статус активности профиля в User Service
}
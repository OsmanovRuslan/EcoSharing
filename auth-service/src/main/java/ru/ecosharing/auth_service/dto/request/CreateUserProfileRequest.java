package ru.ecosharing.auth_service.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * DTO для запроса к User Service на создание профиля пользователя.
 * Отправляется из Auth Service после успешного создания учетных данных.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateUserProfileRequest {

    private UUID userId; // ID, сгенерированный в Auth Service
    private String telegramId;
    private String username;
    private String email;
    private String firstName;
    private String lastName;
    private String phone;
    // Другие поля, необходимые User Service при создании профиля
}
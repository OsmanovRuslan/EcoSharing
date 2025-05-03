package ru.ecosharing.user_service.dto.request;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * DTO для запроса на создание профиля пользователя в User Service.
 * Отправляется из Auth Service после успешного создания учетных данных.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateUserProfileRequest {

    @NotNull(message = "userId не может быть null")
    private UUID userId; // ID, сгенерированный в Auth Service

    @NotBlank(message = "Имя пользователя не может быть пустым")
    @Size(min = 3, max = 50, message = "Логин должен быть от 3 до 50 символов")
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "Логин может содержать только латинские буквы, цифры и подчеркивание")
    private String username;

    @NotBlank(message = "Email не может быть пустым")
    @Email(message = "Email должен быть корректным")
    @Size(max = 100)
    private String email;

    // Поля профиля (могут быть null, если приходят из Telegram без них)
    @Size(max = 100, message = "Имя должно быть до 100 символов")
    private String firstName;

    @Size(max = 100, message = "Фамилия должна быть до 100 символов")
    private String lastName;

    @Size(max = 20, message = "Телефон должен быть до 20 символов")
    private String phone;

    // Другие поля, необходимые User Service при создании профиля (например, location из Telegram)
    // private String location;
}
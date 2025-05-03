package ru.ecosharing.auth_service.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO для запроса регистрации нового пользователя.
 * Содержит как данные для Auth Service (логин, пароль),
 * так и данные для профиля в User Service (имя, фамилия, телефон).
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class RegisterRequest {

    // --- Данные для User Service ---
    @NotBlank(message = "Имя не может быть пустым")
    @Size(min = 1, max = 100, message = "Имя должно быть от 1 до 100 символов")
    private String firstName;

    @Size(max = 100, message = "Фамилия должна быть до 100 символов")
    private String lastName; // Опционально

    @Size(max = 20, message = "Телефон должен быть до 20 символов")
    private String phone; // Опционально

    // --- Данные для Auth Service ---
    @NotBlank(message = "Имя пользователя (логин) не может быть пустым")
    @Size(min = 3, max = 50, message = "Логин должен быть от 3 до 50 символов")
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "Логин может содержать только латинские буквы, цифры и подчеркивание")
    private String username;

    @NotBlank(message = "Email не может быть пустым")
    @Email(message = "Email должен быть корректным")
    @Size(max = 100)
    private String email;

    @NotBlank(message = "Пароль не может быть пустым")
    @Size(min = 8, max = 100, message = "Пароль должен быть от 8 до 100 символов")
    // Примерный паттерн: минимум 1 цифра, 1 маленькая, 1 большая буква, 1 спецсимвол, без пробелов
    @Pattern(regexp = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=!\\-_])(?=\\S+$).{8,}$",
            message = "Пароль должен содержать минимум 1 цифру, 1 строчную и 1 заглавную латинскую букву, 1 спецсимвол (@#$%^&+=!-_) и не содержать пробелов.")
    private String password;
}
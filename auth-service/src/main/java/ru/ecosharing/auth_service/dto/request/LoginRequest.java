package ru.ecosharing.auth_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO для запроса аутентификации пользователя по логину и паролю.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class LoginRequest {

    @NotBlank(message = "Логин (username или email) не может быть пустым")
    private String login; // Поле для ввода email

    @NotBlank(message = "Пароль не может быть пустым")
    private String password; // Пароль пользователя
}

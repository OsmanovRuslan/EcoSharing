package ru.ecosharing.user_service.dto.request;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * DTO для обновления профиля текущим пользователем.
 * Содержит поля, которые пользователь может изменять.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserProfileRequest {

    @Size(min = 1, max = 100, message = "Имя должно быть от 1 до 100 символов")
    private String firstName;

    @Size(max = 100, message = "Фамилия должна быть до 100 символов")
    private String lastName;

    @Size(max = 255, message = "URL аватара должен быть до 255 символов")
    // @URL // Можно добавить валидацию URL, если нужно
    private String avatarUrl;

    @Size(max = 20, message = "Телефон должен быть до 20 символов")
    // @Pattern(regexp = "^\\+?[0-9\\s()-]{10,20}$") // Пример паттерна для телефона
    private String phone;

    @Size(max = 1000, message = "'О себе' должно быть до 1000 символов")
    private String about; // Переименовал bio в about для консистентности с UserProfile

    @Size(max = 255, message = "Локация должна быть до 255 символов")
    private String location;

    @Past(message = "Дата рождения должна быть в прошлом")
    private LocalDate birthDate;

    // Настройки сюда не включаем, для них отдельные эндпоинты/DTO
}
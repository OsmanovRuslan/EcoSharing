package ru.ecosharing.auth_service.dto.telegram;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO, содержащий данные пользователя, полученные из Telegram WebApp initData.
 * Используется, когда пользователь еще не зарегистрирован или не привязан к Telegram.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class TelegramUserData {

    private String telegramId; // Уникальный ID пользователя в Telegram
    private String firstName;  // Имя (может быть пустым)
    private String lastName;   // Фамилия (может быть пустой)
    private String username;   // Username в Telegram (может быть пустым)
}

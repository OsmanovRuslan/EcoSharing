package ru.ecosharing.auth_service.dto.enumeration;

/**
 * Перечисление возможных типов уведомлений, отправляемых через NotificationClient.
 */
public enum NotificationType {
    REGISTRATION_COMPLETE, // Успешная регистрация
    PASSWORD_CHANGED,      // Пароль изменен
    WELCOME_TELEGRAM,      // Приветствие при регистрации через Telegram
    LOGIN_ALERT            // Оповещение о входе (если нужно)
    // Добавьте другие типы по мере необходимости
}
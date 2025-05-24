package ru.ecosharing.telegram_bot_service.model;

/**
 * Типы уведомлений, которые может отправлять система
 */
public enum NotificationType {

    // Уведомления о регистрации/аккаунте
    REGISTRATION_COMPLETE,   // Регистрация успешно завершена
    EMAIL_VERIFICATION,      // Подтверждение email
    PROFILE_UPDATE,          // Обновление профиля

    // Уведомления об аренде
    RENTAL_REQUEST,          // Новый запрос на аренду
    RENTAL_APPROVED,         // Аренда подтверждена
    RENTAL_REJECTED,         // Аренда отклонена

    // Уведомления о платежах
    PAYMENT_SUCCESS,         // Успешная оплата
    PAYMENT_FAILED,          // Ошибка оплаты

    // Уведомления о сообщениях
    NEW_MESSAGE,             // Новое сообщение

    // Уведомления о листингах
    LISTING_APPROVED,        // Объявление одобрено
    LISTING_REJECTED,        // Объявление отклонено

    // Системные уведомления
    SYSTEM_MAINTENANCE,      // Техническое обслуживание
    SYSTEM_UPDATE            // Обновление системы
}
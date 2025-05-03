package ru.ecosharing.notification_service.model.enums;

public enum NotificationType {

    // Из Auth Service
    REGISTRATION_COMPLETE, PASSWORD_CHANGED, WELCOME_TELEGRAM,

    // Из Listing Service (примеры)
    LISTING_APPROVED, LISTING_REJECTED, LISTING_EXPIRED,

    // Из Subscription Service (примеры)
    SUBSCRIPTION_STARTED, SUBSCRIPTION_ENDING, SUBSCRIPTION_EXPIRED, PAYMENT_SUCCESS, PAYMENT_FAILED,

    // Системные
    SYSTEM_MAINTENANCE, SYSTEM_UPDATE,

    // Общее
    GENERIC_NOTIFICATION
}

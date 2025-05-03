package ru.ecosharing.telegram_bot_service.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import ru.ecosharing.telegram_bot_service.model.NotificationType;
import ru.ecosharing.telegram_bot_service.client.UserServiceClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Сервис для отправки уведомлений пользователям через Telegram
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final TelegramBotService telegramBotService;
    private final UserServiceClient userServiceClient;

    /**
     * Отправляет уведомление пользователю
     *
     * @param telegramId ID пользователя в Telegram
     * @param type Тип уведомления
     * @param params Параметры для шаблона уведомления
     * @return true, если уведомление успешно отправлено
     */
    public boolean sendNotification(String telegramId, NotificationType type, Map<String, String> params) {
        return sendNotification(telegramId, type, params, false);
    }

    /**
     * Отправляет уведомление пользователю с опцией добавления кнопки WebApp
     *
     * @param telegramId ID пользователя в Telegram
     * @param type Тип уведомления
     * @param params Параметры для шаблона уведомления
     * @param attachWebAppButton Добавлять ли кнопку для открытия WebApp
     * @return true, если уведомление успешно отправлено
     */
    public boolean sendNotification(String telegramId, NotificationType type, Map<String, String> params, boolean attachWebAppButton) {
        try {
//            // Проверяем, что пользователь существует и разрешил уведомления
//            if (!userServiceClient.isUserExistsAndNotificationsEnabled(telegramId)) {
//                log.info("User {} does not exist or has disabled notifications", telegramId);
//                return false;
//            }

            // Получаем текст уведомления по шаблону
            String notificationText = getNotificationText(type, params);

            // Если нужно добавить кнопку WebApp, создаем клавиатуру
            if (attachWebAppButton) {
                InlineKeyboardMarkup keyboardMarkup = createWebAppKeyboard(params);
                return telegramBotService.sendMessage(telegramId, notificationText, keyboardMarkup);
            } else {
                return telegramBotService.sendMessage(telegramId, notificationText);
            }
        } catch (Exception e) {
            log.error("Error sending notification to user {}: {}", telegramId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Отправляет уведомление пользователю по его идентификатору в системе
     *
     * @param userId ID пользователя в системе
     * @param type Тип уведомления
     * @param params Параметры для шаблона уведомления
     * @return true, если уведомление успешно отправлено
     */
    public boolean sendNotificationByUserId(String userId, NotificationType type, Map<String, String> params) {
        try {
            // Получаем telegramId пользователя
            String telegramId = userServiceClient.getTelegramIdByUserId(userId);

            if (telegramId == null || telegramId.isEmpty()) {
                log.info("User {} has no associated Telegram account", userId);
                return false;
            }

            return sendNotification(telegramId, type, params);
        } catch (Exception e) {
            log.error("Error sending notification to user with ID {}: {}", userId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Отправляет групповое уведомление нескольким пользователям
     *
     * @param telegramIds Список ID пользователей в Telegram
     * @param type Тип уведомления
     * @param params Параметры для шаблона уведомления
     * @return Количество успешно отправленных уведомлений
     */
    public int sendBulkNotification(List<String> telegramIds, NotificationType type, Map<String, String> params) {
        int successCount = 0;

        for (String telegramId : telegramIds) {
            if (sendNotification(telegramId, type, params)) {
                successCount++;
            }
        }

        return successCount;
    }

    /**
     * Создает клавиатуру с кнопкой для открытия WebApp
     */
    private InlineKeyboardMarkup createWebAppKeyboard(Map<String, String> params) {
        InlineKeyboardMarkup keyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        List<InlineKeyboardButton> row = new ArrayList<>();

        InlineKeyboardButton appButton = new InlineKeyboardButton();
        appButton.setText("🌱 Открыть EcoSharing");

        // Если в параметрах есть screenPath, добавляем его к URL
        String screenPath = params.getOrDefault("screenPath", "");
        if (!screenPath.isEmpty()) {
            appButton.setUrl("https://t.me/ecosharing_bot/app?startapp=" + screenPath);
        } else {
            appButton.setUrl("https://t.me/ecosharing_bot/app");
        }

        row.add(appButton);
        keyboard.add(row);
        keyboardMarkup.setKeyboard(keyboard);

        return keyboardMarkup;
    }

    /**
     * Получает текст уведомления по типу и параметрам
     */
    private String getNotificationText(NotificationType type, Map<String, String> params) {
        String template = getTemplateByType(type);

        // Заменяем плейсхолдеры в шаблоне на значения из params
        for (Map.Entry<String, String> entry : params.entrySet()) {
            template = template.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }

        return template;
    }

    /**
     * Возвращает шаблон уведомления по типу
     */
    private String getTemplateByType(NotificationType type) {
        return switch (type) {
            // Уведомления о регистрации
            case REGISTRATION_COMPLETE -> "✅ <b>Регистрация завершена</b>\n\n" +
                    "Добро пожаловать в EcoSharing! Ваш аккаунт успешно зарегистрирован.\n\n" +
                    "Теперь вы можете публиковать объявления и арендовать вещи.";
            case EMAIL_VERIFICATION -> "📧 <b>Подтверждение email</b>\n\n" +
                    "Для завершения регистрации подтвердите ваш email. " +
                    "Код подтверждения: <code>{{verificationCode}}</code>";

            // Уведомления об аренде
            case RENTAL_REQUEST -> "🔄 <b>Новый запрос на аренду</b>\n\n" +
                    "Пользователь хочет арендовать: <b>{{itemName}}</b>\n" +
                    "Период: {{startDate}} - {{endDate}}\n" +
                    "Стоимость: {{price}} {{currency}}\n\n" +
                    "Подробности доступны в приложении.";
            case RENTAL_APPROVED -> "✅ <b>Аренда подтверждена</b>\n\n" +
                    "Ваш запрос на аренду <b>{{itemName}}</b> подтвержден владельцем.\n" +
                    "Период: {{startDate}} - {{endDate}}\n\n" +
                    "Для продолжения необходимо оплатить аренду в приложении.";
            case RENTAL_REJECTED -> "❌ <b>Аренда отклонена</b>\n\n" +
                    "К сожалению, ваш запрос на аренду <b>{{itemName}}</b> был отклонен.\n\n" +
                    "Причина: {{reason}}\n\n" +
                    "Вы можете выбрать другие предложения в приложении.";
            case RENTAL_CANCELED -> "🚫 <b>Аренда отменена</b>\n\n" +
                    "Аренда <b>{{itemName}}</b> была отменена.\n\n" +
                    "Причина: {{reason}}";
            case RENTAL_STARTED -> "🎉 <b>Аренда началась</b>\n\n" +
                    "Ваша аренда <b>{{itemName}}</b> началась сегодня.\n" +
                    "Дата окончания: {{endDate}}\n\n" +
                    "Приятного использования!";
            case RENTAL_ENDING -> "⏰ <b>Аренда скоро закончится</b>\n\n" +
                    "Напоминаем, что ваша аренда <b>{{itemName}}</b> закончится {{endDate}}.\n\n" +
                    "Пожалуйста, подготовьтесь к возврату вещи.";
            case RENTAL_COMPLETED -> "✓ <b>Аренда завершена</b>\n\n" +
                    "Ваша аренда <b>{{itemName}}</b> успешно завершена.\n\n" +
                    "Спасибо за использование EcoSharing! Будем рады вашим отзывам.";

            // Уведомления о платежах
            case PAYMENT_SUCCESS -> "💰 <b>Оплата успешна</b>\n\n" +
                    "Оплата аренды <b>{{itemName}}</b> успешно выполнена.\n" +
                    "Сумма: {{amount}} {{currency}}\n\n" +
                    "Детали аренды доступны в приложении.";
            case PAYMENT_FAILED -> "❗ <b>Ошибка оплаты</b>\n\n" +
                    "При оплате аренды <b>{{itemName}}</b> произошла ошибка.\n\n" +
                    "Причина: {{reason}}\n\n" +
                    "Пожалуйста, попробуйте другой способ оплаты или обратитесь в поддержку.";

            // Уведомления о сообщениях
            case NEW_MESSAGE -> "💬 <b>Новое сообщение</b>\n\n" +
                    "У вас новое сообщение от пользователя {{senderName}}:\n\n" +
                    "\"{{messageText}}\"\n\n" +
                    "Ответить можно в приложении.";

            // Уведомления о листингах
            case LISTING_APPROVED -> "✅ <b>Объявление одобрено</b>\n\n" +
                    "Ваше объявление \"{{listingTitle}}\" прошло модерацию и теперь доступно всем пользователям.";
            case LISTING_REJECTED -> "❌ <b>Объявление отклонено</b>\n\n" +
                    "К сожалению, ваше объявление \"{{listingTitle}}\" не прошло модерацию.\n\n" +
                    "Причина: {{reason}}\n\n" +
                    "Вы можете отредактировать объявление и отправить его на повторную модерацию.";

            // Системные уведомления
            case SYSTEM_MAINTENANCE -> "🔧 <b>Техническое обслуживание</b>\n\n" +
                    "Уважаемый пользователь, {{startDate}} с {{startTime}} до {{endTime}} " +
                    "будет проводиться техническое обслуживание системы.\n\n" +
                    "В это время сервис может быть недоступен. Приносим извинения за возможные неудобства.";
            case SYSTEM_UPDATE -> "🆙 <b>Обновление системы</b>\n\n" +
                    "Мы обновили наше приложение! Новая версия включает:\n\n" +
                    "{{features}}\n\n" +
                    "Обновите приложение, чтобы воспользоваться новыми функциями.";

            // Дефолтное сообщение для неопознанных типов
            default -> "📢 <b>Уведомление</b>\n\n" +
                    "У вас новое уведомление от EcoSharing.";
        };
    }
}
package ru.ecosharing.telegram_bot_service.bot;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.webapp.WebAppInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Обработчик обновлений от Telegram (кроме платежей).
 * Основная задача - отвечать на команды и предоставлять кнопку для входа в Mini App.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TelegramUpdateHandler {

    @Value("${bot.mini-app-url}") // URL Mini App из конфигурации
    private String MINI_APP_URL;

    /**
     * Главный метод обработки обновлений (кроме платежей).
     */
    public BotApiMethod<?> handleUpdate(Update update) {
        try {
            if (update.hasMessage() && update.getMessage().hasText()) {
                return handleTextMessage(update.getMessage());
            } else if (update.hasCallbackQuery()) {
                return handleCallbackQuery(update.getCallbackQuery());
            } else if (update.hasMyChatMember()) {
                handleMyChatMember(update);
                return null;
            } else {
                log.debug("Unhandled update type received: {}", update);
                return null;
            }
        } catch (Exception e) {
            log.error("Error handling update ID {}", update.getUpdateId(), e);
            Long chatId = getChatIdFromUpdate(update);
            if (chatId != null) {
                return new SendMessage(chatId.toString(), "Произошла ошибка обработки команды.");
            }
            return null;
        }
    }

    /**
     * Обрабатывает текстовые сообщения.
     */
    private BotApiMethod<?> handleTextMessage(Message message) {
        String text = message.getText();
        Long chatId = message.getChatId();
        User user = message.getFrom();

        log.info("Received message from User ID: {}, Chat ID: {}, Text: '{}'", user.getId(), chatId, text);

        if (text.startsWith("/")) {
            String command = text.split(" ")[0].toLowerCase();
            switch (command) {
                case "/start":
                    return handleStartCommand(chatId);
                case "/app":
                case "/webapp":
                    return createOpenAppMessage(chatId, "Нажмите кнопку, чтобы открыть приложение:");
                default:
                    return createOpenAppMessage(chatId, "Привет! 👋 Для работы с сервисом используйте наше приложение:");
            }
        } else {
            // Ответ на обычный текст - предлагаем открыть приложение
            return createOpenAppMessage(chatId, "Привет! 👋 Для работы с сервисом используйте наше приложение:");
        }
    }

    /**
     * Обрабатывает команду /start - отправляет приветствие и кнопку Mini App.
     */
    private BotApiMethod<?> handleStartCommand(Long chatId) {
        log.info("User {} started the bot", chatId);
        String welcomeText = "👋 Добро пожаловать в EcoSharing!\n\n" +
                "Здесь вы можете арендовать вещи или делиться своими.\n\n" +
                "Нажмите кнопку ниже, чтобы начать!";
        return createOpenAppMessage(chatId, welcomeText);
    }

    /**
     * Создает сообщение с кнопкой для открытия Mini App.
     */
    private SendMessage createOpenAppMessage(Long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(text);
        message.setReplyMarkup(createOpenAppKeyboard());
        message.setParseMode("HTML");
        return message;
    }

    private InlineKeyboardMarkup createOpenAppKeyboard() {
        InlineKeyboardMarkup keyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        List<InlineKeyboardButton> row = new ArrayList<>();

        // Создаем URL-кнопку вместо WebApp кнопки
        InlineKeyboardButton appButton = new InlineKeyboardButton();
        appButton.setText("🌱 Открыть EcoSharing App");
        appButton.setUrl(MINI_APP_URL);
        row.add(appButton);
        keyboard.add(row);

        // Добавляем кнопку помощи
        List<InlineKeyboardButton> secondRow = new ArrayList<>();
        InlineKeyboardButton helpButton = new InlineKeyboardButton();
        helpButton.setText("❓ Помощь");
        helpButton.setCallbackData("help");
        secondRow.add(helpButton);
        keyboard.add(secondRow);

        keyboardMarkup.setKeyboard(keyboard);
        return keyboardMarkup;
    }


    /**
     * Обрабатывает нажатия на inline кнопки (CallbackQuery).
     * Оставляем заготовку, если понадобятся другие кнопки кроме Mini App.
     */
    private BotApiMethod<?> handleCallbackQuery(CallbackQuery callbackQuery) {
        String callbackData = callbackQuery.getData();
        Long chatId = callbackQuery.getMessage().getChatId();
        Integer messageId = callbackQuery.getMessage().getMessageId();
        User user = callbackQuery.getFrom();

        log.info("Callback query from User ID: {}, Chat ID: {}, Msg ID: {}, Data: '{}'",
                user.getId(), chatId, messageId, callbackData);

        if ("help".equals(callbackData)) {
            // Редактируем существующее сообщение
            EditMessageText editMessage = new EditMessageText();
            editMessage.setChatId(chatId.toString());
            editMessage.setMessageId(messageId);
            editMessage.setText("📱 EcoSharing - ваш сервис для аренды вещей.\n\n" +
                    "🌱 Делитесь вещами\n" +
                    "💰 Экономьте деньги\n" +
                    "🌎 Берегите планету\n" +
                    "🔍 Находите что угодно\n");

            // Сохраняем кнопку мини-приложения
            InlineKeyboardMarkup keyboardMarkup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
            List<InlineKeyboardButton> row = new ArrayList<>();

            InlineKeyboardButton appButton = new InlineKeyboardButton();
            appButton.setText("🌱 Открыть EcoSharing");
            appButton.setUrl(MINI_APP_URL);
            row.add(appButton);
            keyboard.add(row);

            // Добавляем кнопку возврата
            List<InlineKeyboardButton> secondRow = new ArrayList<>();
            InlineKeyboardButton backButton = new InlineKeyboardButton();
            backButton.setText("◀️ Назад");
            backButton.setCallbackData("back");
            secondRow.add(backButton);
            keyboard.add(secondRow);

            keyboardMarkup.setKeyboard(keyboard);
            editMessage.setReplyMarkup(keyboardMarkup);

            return editMessage;
        } else if ("back".equals(callbackData)) {
            // Возвращаемся к основному сообщению
            EditMessageText editMessage = new EditMessageText();
            editMessage.setChatId(chatId.toString());
            editMessage.setMessageId(messageId);
            editMessage.setText("👋 Добро пожаловать в EcoSharing!\n\n" +
                    "Здесь вы можете арендовать вещи или делиться своими.\n\n" +
                    "Нажмите кнопку ниже, чтобы начать!");

            // Восстанавливаем оригинальные кнопки
            InlineKeyboardMarkup keyboardMarkup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
            List<InlineKeyboardButton> row = new ArrayList<>();

            InlineKeyboardButton appButton = new InlineKeyboardButton();
            appButton.setText("🌱 Открыть EcoSharing");
            appButton.setUrl(MINI_APP_URL);
            row.add(appButton);
            keyboard.add(row);

            List<InlineKeyboardButton> secondRow = new ArrayList<>();
            InlineKeyboardButton helpButton = new InlineKeyboardButton();
            helpButton.setText("❓ Помощь");
            helpButton.setCallbackData("help");
            secondRow.add(helpButton);
            keyboard.add(secondRow);

            keyboardMarkup.setKeyboard(keyboard);
            editMessage.setReplyMarkup(keyboardMarkup);

            return editMessage;
        }
        log.warn("Received unhandled callback data: {}", callbackData);
        return null;
    }

    /**
     * Обрабатывает событие изменения статуса пользователя в чате.
     */
    private void handleMyChatMember(Update update) {
        String oldStatus = update.getMyChatMember().getOldChatMember().getStatus();
        String newStatus = update.getMyChatMember().getNewChatMember().getStatus();
        Long userId = update.getMyChatMember().getFrom().getId();
        Long chatId = update.getMyChatMember().getChat().getId();

        log.info("Chat Member Status change: User ID: {}, Chat ID: {}. From '{}' to '{}'",
                userId, chatId, oldStatus, newStatus);

        if ("kicked".equals(newStatus) || "left".equals(newStatus)) {
            log.info("User {} blocked or left the bot.", userId);
            // Действия при блокировке (например, деактивация уведомлений в user-service)
            // userServiceClient.disableNotifications(userId);
        } else if (("member".equals(newStatus) || "administrator".equals(newStatus) || "creator".equals(newStatus))) {
            log.info("User {} started or unblocked the bot.", userId);
            // Действия при старте/разблокировке
            // userServiceClient.enableNotifications(userId);
        }
    }

    // Вспомогательный метод для извлечения Chat ID
    private Long getChatIdFromUpdate(Update update) {
        if (update.hasMessage()) return update.getMessage().getChatId();
        if (update.hasEditedMessage()) return update.getEditedMessage().getChatId();
        if (update.hasCallbackQuery() && update.getCallbackQuery().getMessage() != null) return update.getCallbackQuery().getMessage().getChatId();
        return null;
    }

}
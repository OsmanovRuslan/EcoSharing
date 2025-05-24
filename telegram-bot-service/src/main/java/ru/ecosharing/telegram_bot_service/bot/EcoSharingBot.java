package ru.ecosharing.telegram_bot_service.bot;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updates.SetWebhook;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.starter.SpringWebhookBot;

import java.io.Serializable;

@Slf4j
@Component
public class EcoSharingBot extends SpringWebhookBot {

    private final TelegramPaymentHandler paymentHandler;
    private final TelegramUpdateHandler updateHandler;

    private final String botToken;
    private final String botUsername;
    private final String botPath;

    public EcoSharingBot(
            SetWebhook setWebhook,
            TelegramPaymentHandler paymentHandler,
            TelegramUpdateHandler updateHandler,
            @Value("${bot.token}") String botToken,
            @Value("${bot.username}") String botUsername,
            @Value("${bot.webhook-path}") String botPath) {
        super(setWebhook);
        this.paymentHandler = paymentHandler;
        this.updateHandler = updateHandler;
        this.botToken = botToken;
        this.botUsername = botUsername;
        this.botPath = botPath;
    }

    @Override
    public BotApiMethod<?> onWebhookUpdateReceived(Update update) {
        try {
            log.info("Processing update ID: {} from Kafka", update.getUpdateId());

            // PreCheckoutQuery (только для НЕ-Stars платежей, вызываем для совместимости/логирования)
            if (update.hasPreCheckoutQuery()) {
                log.info("Handling PreCheckoutQuery ID: {}", update.getPreCheckoutQuery().getId());
                return paymentHandler.handlePreCheckoutQuery(update.getPreCheckoutQuery());
            }

            // SuccessfulPayment (ОБРАБАТЫВАЕМ ТОЛЬКО STARS)
            if (update.hasMessage() && update.getMessage().hasSuccessfulPayment()) {
                log.info("Handling SuccessfulPayment from user {}", update.getMessage().getFrom().getId());
                return handleSuccessfulPayment(update.getMessage());
            }

            // Другие обновления (сообщения, колбэки и т.д.)
            log.debug("Passing update to UpdateHandler");
            return updateHandler.handleUpdate(update);

        } catch (Exception e) {
            log.error("Error processing update ID: {}", update.getUpdateId(), e);
            Long chatId = getChatIdFromUpdate(update);
            if (chatId != null) {
                return new SendMessage(chatId.toString(),
                        "Произошла внутренняя ошибка. Пожалуйста, попробуйте позже.");
            }
            return null;
        }
    }

    private BotApiMethod<?> handleSuccessfulPayment(Message message) {
        String userId = message.getFrom().getId().toString();
        log.info("Calling PaymentHandler for successful payment from user {}", userId);
        // Передаем обработку платежей в PaymentHandler
        return paymentHandler.handleSuccessfulPayment(userId, message.getSuccessfulPayment());
    }

    // Метод для безопасного извлечения Chat ID
    private Long getChatIdFromUpdate(Update update) {
        if (update.hasMessage()) return update.getMessage().getChatId();
        if (update.hasEditedMessage()) return update.getEditedMessage().getChatId();
        if (update.hasCallbackQuery() && update.getCallbackQuery().getMessage() != null) return update.getCallbackQuery().getMessage().getChatId();
        if (update.hasChannelPost()) return update.getChannelPost().getChatId();
        if (update.hasEditedChannelPost()) return update.getEditedChannelPost().getChatId();
        return null;
    }

    // --- Execute Methods ---

    public <T extends Serializable, Method extends BotApiMethod<T>> T executeMethod(Method method) {
        if (method == null) {
            log.warn("Attempted to execute a null BotApiMethod.");
            return null;
        }

        // Добавьте логирование деталей метода
        if (method instanceof SendMessage) {
            SendMessage sendMessage = (SendMessage) method;
            log.info("Executing SendMessage to chatId: {}, text: '{}'",
                    sendMessage.getChatId(), sendMessage.getText());
        }

        try {
            // Используем метод execute() из AbsSender (родительский класс)
            T result = this.execute(method);
            log.debug("Executed API method: {}. Result: {}", method.getClass().getSimpleName(), result != null ? "Success" : "Null/Failure");
            return result;
        } catch (TelegramApiException e) {
            log.error("Failed to execute Telegram API method {}: {}", method.getClass().getSimpleName(), e.getMessage(), e);
            return null;
        } catch (Exception e) {
            log.error("Unexpected error executing Telegram API method {}: {}", method.getClass().getSimpleName(), e.getMessage(), e);
            return null;
        }
    }

    public void executeMethodAsync(BotApiMethod<?> method) {
        if (method == null) return;
        try {
            // В реальном приложении лучше использовать ExecutorService для асинхронности
            executeMethod(method);
        } catch (Exception e) {
            // Ошибка уже залогирована в executeMethod
        }
    }

    @Override
    public String getBotPath() {
        return botPath;
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    @Override
    public String getBotToken() {
        return botToken;
    }

}
package ru.ecosharing.telegram_bot_service.bot;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.updates.SetWebhook;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.BotSession; // Импорт для BotSession
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

/**
 * Инициализатор бота. Регистрирует бота с вебхуком при старте приложения.
 * Замечание: telegrambots-spring-boot-starter может делать это автоматически,
 * если не нужна кастомная логика регистрации. Этот класс обеспечивает явный контроль.
 */
@Slf4j
@Component
public class BotInitializer {

    @Autowired
    private EcoSharingBot ecoSharingBot;

    @Value("${bot.disable-on-startup:false}")
    private boolean disableOnStartup;

    @Value("${app.webhook-url}")
    private String webhookUrl;

    @Value("${bot.secret-token}")
    private String secretToken;

    @Value("${bot.drop-pending-updates}")
    private boolean dropPendingUpdates;

    @Value("${bot.max-connections}")
    private Integer maxConnections;

    @EventListener({ContextRefreshedEvent.class})
    public void init() {
        if (disableOnStartup) {
            log.info("Bot initialization is disabled by configuration 'bot.disable-on-startup=true'");
            return;
        }

        log.info("Initializing Telegram bot webhook...");
        try {
            TelegramBotsApi telegramBotsApi = new TelegramBotsApi(DefaultBotSession.class);

            // Создаем объект SetWebhook с нашим URL
            SetWebhook setWebhook = SetWebhook.builder()
                    .url(webhookUrl)
                    .maxConnections(maxConnections)
                    .dropPendingUpdates(dropPendingUpdates)
                    .secretToken(secretToken)
                    .build();

            telegramBotsApi.registerBot(ecoSharingBot, setWebhook);
            log.info("EcoSharingBot successfully registered with webhook at: {}", setWebhook.getUrl());

        } catch (TelegramApiException e) {
            log.error("Error registering the bot or setting webhook: {}", e.getMessage(), e);
            // Можно добавить логику повторной попытки или остановки приложения
        } catch (Exception e) {
            log.error("Unexpected error during bot initialization: {}", e.getMessage(), e);
        }
    }
}
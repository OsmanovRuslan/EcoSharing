package ru.ecosharing.telegram_bot_service.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.web.client.RestTemplate;
import org.telegram.telegrambots.meta.api.methods.updates.SetWebhook;
import ru.ecosharing.telegram_bot_service.bot.EcoSharingBot;
import ru.ecosharing.telegram_bot_service.bot.TelegramPaymentHandler;
import ru.ecosharing.telegram_bot_service.bot.TelegramUpdateHandler;

/**
 * Конфигурация для Telegram бота.
 * Замечание: Большинство настроек (токен, username, webhook-path) теперь берутся
 * из telegram.bots.* properties благодаря telegrambots-spring-boot-starter.
 * Этот класс может быть нужен для создания кастомных бинов, как SetWebhook,
 * или для дополнительной конфигурации.
 */
@Slf4j
@Configuration
public class TelegramBotConfig {

    @Value("${bot.username:Test_ecoshir_bot}")
    private String botUsername;

    @Value("${bot.token}")
    private String botToken;

    @Value("bot.mini-app-url")
    private String botPath;

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    /**
     * Создает объект SetWebhook для регистрации.
     * URL формируется из базового URL gateway и пути вебхука бота.
     * @return сконфигурированный объект SetWebhook
     */
    @Bean
    public SetWebhook setWebhook(@Value("${app.webhook-url}") String webhookUrl) {
        log.info("Configuring webhook for Telegram bot at: {}", webhookUrl);
        return SetWebhook.builder()
                .url(webhookUrl)
                .build();
    }


    @Bean
    public EcoSharingBot springWebhookBot(
            SetWebhook setWebhook,
            TelegramPaymentHandler telegramPaymentHandler,
            TelegramUpdateHandler telegramUpdateHandler) {

        EcoSharingBot bot = new EcoSharingBot(
                setWebhook,
                telegramPaymentHandler,
                telegramUpdateHandler,
                botToken,
                botUsername,
                botPath);

        log.info("Telegram bot initialized with username: {}", botUsername);
        return bot;
    }
}
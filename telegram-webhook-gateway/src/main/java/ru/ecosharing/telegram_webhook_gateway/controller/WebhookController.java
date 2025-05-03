package ru.ecosharing.telegram_webhook_gateway.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.ecosharing.telegram_webhook_gateway.service.TelegramWebhookService;

import javax.annotation.PostConstruct;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/webhook")
public class WebhookController {

    private final TelegramWebhookService telegramWebhookService;

    // Получаем секретный токен из конфигурации (его нужно задать при установке вебхука)
    @Value("${bot.secret-token}")
    private String expectedSecretToken;

    @Value("${bot.token}")
    private String botToken;

    @Value("${app.webhook-url}")
    private String webhookUrl;

    private static final String TELEGRAM_SECRET_TOKEN_HEADER = "X-Telegram-Bot-Api-Secret-Token";

    /**
     * Настраиваем вебхук при запуске приложения
     */
    @PostConstruct
    public void setupWebhook() {
        try {
            // Сначала удаляем текущий вебхук
            boolean deleted = telegramWebhookService.deleteWebhook(true);
            log.info("Deleting current webhook: {}", deleted ? "success" : "failed");

            // Затем устанавливаем новый
            boolean success = telegramWebhookService.setWebhook(webhookUrl);

            if (success) {
                log.info("Telegram webhook set successfully to: {}", webhookUrl);

                // Проверяем информацию о вебхуке
                Map<String, Object> info = telegramWebhookService.getWebhookInfo();
                log.info("Current webhook info: {}", info);
            } else {
                log.error("Failed to set Telegram webhook!");
            }
        } catch (Exception e) {
            log.error("Error setting up webhook", e);
        }
    }

    // Динамически используем путь из конфигурации
    @PostMapping("/webhook")
    public ResponseEntity<String> webhook(
            @RequestBody Update update,
            @RequestHeader(value = TELEGRAM_SECRET_TOKEN_HEADER, required = false) String secretTokenHeader) {
        System.out.println("ТУт");
        // 1. Проверка секретного токена (ОБЯЗАТЕЛЬНО для безопасности)
        if (!isValidSecretToken(secretTokenHeader)) {
            log.warn("Invalid or missing secret token received. Header: '{}'", secretTokenHeader);
            // Не отвечаем ошибкой 401, чтобы не раскрывать факт использования токена.
            // Можно вернуть 200 ОК, но ничего не делать, или 403 Forbidden.
            // Telegram рекомендует игнорировать такие запросы или отвечать 403.
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Invalid secret token");
        }

        // 2. Логирование ID и типа апдейта
        String updateType = telegramWebhookService.determineUpdateType(update);
        log.info("Received validated update ID: {}, Type: {}", update.getUpdateId(), updateType);
        // Детали апдейта логируем на DEBUG уровне, если необходимо
        // log.debug("Update details: {}", update);

        // 3. Асинхронная отправка в Kafka
        telegramWebhookService.processUpdate(update);

        // 4. Быстрый ответ Telegram
        // Telegram игнорирует тело ответа, главное - статус 200 OK.
        return ResponseEntity.ok("OK");
    }

    private boolean isValidSecretToken(String receivedToken) {
        // Если токен не задан в конфигурации, проверка отключается (НЕ РЕКОМЕНДУЕТСЯ)
        System.out.println("ТУт1");
        if (!StringUtils.hasText(expectedSecretToken)) {
            log.warn("Webhook secret token is not configured! Security risk!");
            return true; // Пропускаем проверку, если токен не задан
        }
        // Сравниваем полученный токен с ожидаемым
        return expectedSecretToken.equals(receivedToken);
    }
}
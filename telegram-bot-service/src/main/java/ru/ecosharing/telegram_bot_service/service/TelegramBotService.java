package ru.ecosharing.telegram_bot_service.service;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updates.DeleteWebhook;
import org.telegram.telegrambots.meta.api.methods.updates.GetWebhookInfo;
import org.telegram.telegrambots.meta.api.methods.updates.SetWebhook;
import org.telegram.telegrambots.meta.api.objects.WebhookInfo;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

import lombok.extern.slf4j.Slf4j;
import ru.ecosharing.telegram_bot_service.bot.EcoSharingBot;

@Slf4j
@Service
public class TelegramBotService {

    private final RestTemplate restTemplate;
    private final EcoSharingBot ecoSharingBot; // Используем @Lazy при инъекции

    @Value("${bot.token}")
    private String botToken;

    public TelegramBotService(RestTemplate restTemplate, @Lazy EcoSharingBot ecoSharingBot) {
        this.restTemplate = restTemplate;
        this.ecoSharingBot = ecoSharingBot;
    }

    /**
     * Создает ссылку на оплату через Telegram API (для Stars).
     * Использует RestTemplate для прямого вызова API.
     */
    public String createInvoiceLink(String title, String description, String payload, String currency, int amount) {
        // --- Проверки входных данных (базовые) ---
        if (title == null || description == null || payload == null || currency == null || amount <= 0) {
            log.error("Invalid arguments for createInvoiceLink: title={}, description={}, payload={}, currency={}, amount={}",
                    title, description, payload, currency, amount);
            throw new IllegalArgumentException("Missing required parameters for creating invoice link.");
        }
        if (!"XTR".equalsIgnoreCase(currency)) {
            log.error("Attempted to create invoice link with non-XTR currency: {}", currency);
            throw new IllegalArgumentException("Only XTR currency (Telegram Stars) is supported for invoice links via this method.");
        }
        // --- Конец проверок ---

        String apiUrl = "https://api.telegram.org/bot" + botToken + "/createInvoiceLink";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        List<Map<String, Object>> prices = List.of(Map.of("label", title, "amount", amount)); // Цена должна быть в массиве

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("title", title);
        requestBody.put("description", description);
        requestBody.put("payload", payload);
        requestBody.put("currency", currency); // "XTR"
        requestBody.put("prices", prices);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
        log.info("Requesting Stars invoice link from Telegram API. Payload: {}, Amount: {}", payload, amount);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(apiUrl, request, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null && Boolean.TRUE.equals(response.getBody().get("ok"))) {
                String invoiceLink = (String) response.getBody().get("result");
                log.info("Successfully created Stars invoice link (slug): {}", invoiceLink);
                return invoiceLink;
            } else {
                String errorDesc = response.getBody() != null ? response.getBody().getOrDefault("description", "Unknown API error").toString() : "Unknown API error";
                log.error("Failed to create Stars invoice link. Status: {}, Error: {}", response.getStatusCode(), errorDesc);
                throw new RuntimeException("Failed to create invoice link: " + errorDesc);
            }
        } catch (HttpClientErrorException e) {
            log.error("HTTP error creating Stars invoice link: Status {}, Body {}", e.getStatusCode(), e.getResponseBodyAsString(), e);
            throw new RuntimeException("HTTP error: " + e.getMessage());
        } catch (RestClientException e) {
            log.error("Error creating Stars invoice link via RestTemplate", e);
            throw new RuntimeException("Network/RestTemplate error: " + e.getMessage());
        }
    }

    /**
     * Получает информацию о текущем вебхуке бота.
     * Использует метод execute() бота.
     */
    public WebhookInfo getWebhookInfo() {
        log.debug("Requesting webhook info using bot execute method.");
        GetWebhookInfo getWebhookInfoMethod = new GetWebhookInfo();
        try {
            // Используем метод бота для выполнения запроса
            return ecoSharingBot.executeMethod(getWebhookInfoMethod);
        } catch (Exception e) {
            // Ошибка уже залогирована в executeMethod
            return null;
        }
    }

    /**
     * Удаляет текущий вебхук бота.
     * Использует метод execute() бота.
     */
    public boolean deleteWebhook(boolean dropPendingUpdates) {
        log.info("Requesting webhook deletion. Drop pending updates: {}", dropPendingUpdates);
        DeleteWebhook deleteWebhookMethod = DeleteWebhook.builder()
                .dropPendingUpdates(dropPendingUpdates)
                .build();
        try {
            Boolean result = ecoSharingBot.executeMethod(deleteWebhookMethod);
            log.info("Delete webhook result: {}", result);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Устанавливает вебхук для бота.
     * Использует метод execute() бота.
     */
    public boolean setWebhook(String webhookUrl, SetWebhook setWebhookInstance) {
        log.info("Requesting to set webhook to URL: {}", webhookUrl);
        setWebhookInstance.setUrl(webhookUrl); // Убедимся, что URL правильный

        try {
            Boolean result = ecoSharingBot.executeMethod(setWebhookInstance);
            log.info("Set webhook result: {}", result);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Отправляет текстовое сообщение пользователю.
     * Использует метод execute() бота.
     */
    public boolean sendMessage(String chatId, String text) {
        return sendMessage(chatId, text, null);
    }

    /**
     * Отправляет текстовое сообщение пользователю с Inline клавиатурой.
     * Использует метод execute() бота.
     */
    public boolean sendMessage(String chatId, String text, InlineKeyboardMarkup replyMarkup) {
        if (chatId == null || chatId.isEmpty() || text == null || text.isEmpty()) {
            log.warn("Attempted to send message with empty chatId or text.");
            return false;
        }
        SendMessage message = SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .parseMode("HTML") // Используем HTML
                .replyMarkup(replyMarkup) // Может быть null
                .build();
        try {
            // Используем метод executeMethod, который логирует ошибки
            ecoSharingBot.executeMethod(message);
            return true; // Считаем успешным, если не было исключения
        } catch (Exception e) {
            // Ошибка уже залогирована
            return false;
        }
    }

    /**
     * Выполняет любой метод Telegram Bot API.
     * Обертка над executeMethod бота для использования в других сервисах/контроллерах.
     *
     * @param method Метод API для выполнения.
     * @param <T> Тип возвращаемого значения.
     * @param <Method> Тип метода.
     * @return Результат выполнения или null при ошибке.
     */
    public <T extends Serializable, Method extends BotApiMethod<T>> T executeApiMethod(Method method) {
        try {
            return ecoSharingBot.executeMethod(method);
        } catch (Exception e) {
            // Логирование уже внутри executeMethod
            return null;
        }
    }
}
package ru.ecosharing.telegram_webhook_gateway.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.util.concurrent.ListenableFutureCallback;
import org.springframework.web.client.RestTemplate;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramWebhookService {

    private final RestTemplate restTemplate;

    private final KafkaTemplate<String, Update> kafkaTemplate;

    @Value("${telegram.kafka.topic}")
    private String topicName;

    @Value("${bot.token}")
    private String botToken;

    @Value("${bot.max-connections:40}")
    private Integer maxConnections;

    @Value("${bot.drop-pending-updates:false}")
    private Boolean dropPendingUpdates;

    @Value("${bot.secret-token}")
    private String secretToken;

    /**
     * Метод для настройки вебхука бота
     */
    public boolean setWebhook(String webhookUrl) {
        log.info("Setting webhook to URL: {}", webhookUrl);
        String apiUrl = "https://api.telegram.org/bot" + botToken + "/setWebhook";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("url", webhookUrl);

        // Добавляем дополнительные параметры
        requestBody.put("max_connections", maxConnections);
        requestBody.put("drop_pending_updates", dropPendingUpdates);
        requestBody.put("secret_token", secretToken);

        // Определяем, какие типы обновлений получать
        List<String> allowedUpdates = new ArrayList<>();
        allowedUpdates.add("message");
        allowedUpdates.add("callback_query");
        allowedUpdates.add("pre_checkout_query");
        allowedUpdates.add("my_chat_member");
        requestBody.put("allowed_updates", allowedUpdates);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(apiUrl, request, Map.class);
            log.info("SetWebhook response: {}", response.getBody());

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                boolean success = (boolean) response.getBody().get("ok");
                String description = response.getBody().containsKey("description") ?
                        response.getBody().get("description").toString() : "No description";

                log.info("Setting webhook result: {}, description: {}", success, description);
                return success;
            } else {
                log.error("Failed to set webhook. Response: {}", response);
                return false;
            }
        } catch (Exception e) {
            log.error("Error setting webhook", e);
            return false;
        }
    }

    /**
     * Получает информацию о текущем вебхуке
     */
    public Map<String, Object> getWebhookInfo() {
        String apiUrl = "https://api.telegram.org/bot" + botToken + "/getWebhookInfo";

        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(apiUrl, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> result = (Map<String, Object>) response.getBody().get("result");
                log.info("Webhook info: {}", result);
                return result;
            } else {
                log.error("Failed to get webhook info. Response: {}", response);
                return Map.of("error", "Failed to get webhook info");
            }
        } catch (Exception e) {
            log.error("Error getting webhook info", e);
            return Map.of("error", e.getMessage());
        }
    }

    /**
     * Удаляет текущий вебхук
     */
    public boolean deleteWebhook(boolean dropPendingUpdates) {
        String apiUrl = "https://api.telegram.org/bot" + botToken + "/deleteWebhook";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("drop_pending_updates", dropPendingUpdates);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(apiUrl, request, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                boolean success = (boolean) response.getBody().get("ok");
                log.info("Delete webhook result: {}", success);
                return success;
            } else {
                log.error("Failed to delete webhook. Response: {}", response);
                return false;
            }
        } catch (Exception e) {
            log.error("Error deleting webhook", e);
            return false;
        }
    }

    /**
     * Обрабатывает входящее обновление от Telegram и отправляет его в Kafka.
     * Использует CompletableFuture для асинхронной отправки и обработки результата/ошибки.
     *
     * @param update Объект обновления от Telegram.
     */
    public void processUpdate(Update update) {
        try {
            String key = extractKey(update);
            String updateType = determineUpdateType(update);
            log.debug("Attempting to send update to Kafka. Key: {}, Type: {}, Topic: {}", key, updateType, topicName);

            // Асинхронная отправка
            ListenableFuture<SendResult<String, Update>> listenableFuture = kafkaTemplate.send(topicName, key, update);

            // Преобразование ListenableFuture в CompletableFuture
            CompletableFuture<SendResult<String, Update>> future = new CompletableFuture<>();

            listenableFuture.addCallback(new ListenableFutureCallback<>() {
                @Override
                public void onSuccess(SendResult<String, Update> result) {
                    future.complete(result);
                }

                @Override
                public void onFailure(Throwable ex) {
                    future.completeExceptionally(ex);
                }
            });

            // Обработка результата отправки (асинхронно)
            future.whenComplete((result, ex) -> {
                if (ex == null) {
                    log.info("Successfully sent update ID: {} to Kafka topic: {}, partition: {}, offset: {}",
                            update.getUpdateId(),
                            result.getRecordMetadata().topic(),
                            result.getRecordMetadata().partition(),
                            result.getRecordMetadata().offset());
                } else {
                    // Ошибка отправки в Kafka
                    log.error("Failed to send update ID: {} to Kafka. Key: {}, Type: {}",
                            update.getUpdateId(), key, updateType, ex);
                    // Здесь можно добавить логику для fallback (например, запись в DLQ или лог ошибок)
                }
            });

        } catch (Exception e) {
            // Ошибка ДО отправки в Kafka (например, при извлечении ключа)
            log.error("Error preparing update ID: {} for Kafka", update.getUpdateId(), e);
        }
    }

    /**
     * Извлекает ключ для партиционирования из объекта Update.
     * Гарантирует, что обновления для одного чата/пользователя попадут в одну партицию.
     *
     * @param update Объект обновления.
     * @return Строковый ключ (chatId, userId или updateId).
     */
    private String extractKey(Update update) {
        if (update.hasMessage()) {
            return update.getMessage().getChatId().toString();
        } else if (update.hasEditedMessage()) {
            return update.getEditedMessage().getChatId().toString();
        } else if (update.hasChannelPost()) {
            return update.getChannelPost().getChatId().toString();
        } else if (update.hasEditedChannelPost()) {
            return update.getEditedChannelPost().getChatId().toString();
        } else if (update.hasInlineQuery()) {
            return update.getInlineQuery().getFrom().getId().toString();
        } else if (update.hasChosenInlineQuery()) {
            return update.getChosenInlineQuery().getFrom().getId().toString();
        } else if (update.hasCallbackQuery()) {
            // У CallbackQuery может не быть сообщения (например, для игр)
            if (update.getCallbackQuery().getMessage() != null) {
                return update.getCallbackQuery().getMessage().getChatId().toString();
            } else {
                return update.getCallbackQuery().getFrom().getId().toString();
            }
        } else if (update.hasShippingQuery()) {
            return update.getShippingQuery().getFrom().getId().toString();
        } else if (update.hasPreCheckoutQuery()) {
            return update.getPreCheckoutQuery().getFrom().getId().toString();
        } else if (update.hasPollAnswer()) {
            return update.getPollAnswer().getUser().getId().toString();
        } else if (update.hasMyChatMember()) {
            return update.getMyChatMember().getChat().getId().toString();
        } else if (update.hasChatMember()) {
            return update.getChatMember().getChat().getId().toString();
        } else if (update.hasChatJoinRequest()) {
            return update.getChatJoinRequest().getChat().getId().toString();
        }
        // Fallback, если не удалось извлечь более специфичный ключ
        log.warn("Could not extract specific key for update ID: {}. Using updateId as key.", update.getUpdateId());
        return String.valueOf(update.getUpdateId());
    }

    /**
     * Определяет тип обновления для логирования.
     *
     * @param update Объект обновления.
     * @return Строка, представляющая тип обновления.
     */
    public String determineUpdateType(Update update) {
        if (update.hasMessage()) return update.getMessage().hasSuccessfulPayment() ? "SUCCESSFUL_PAYMENT" : "MESSAGE";
        if (update.hasEditedMessage()) return "EDITED_MESSAGE";
        if (update.hasChannelPost()) return "CHANNEL_POST";
        if (update.hasEditedChannelPost()) return "EDITED_CHANNEL_POST";
        if (update.hasInlineQuery()) return "INLINE_QUERY";
        if (update.hasChosenInlineQuery()) return "CHOSEN_INLINE_QUERY";
        if (update.hasCallbackQuery()) return "CALLBACK_QUERY";
        if (update.hasShippingQuery()) return "SHIPPING_QUERY";
        if (update.hasPreCheckoutQuery()) return "PRE_CHECKOUT_QUERY";
        if (update.hasPollAnswer()) return "POLL_ANSWER";
        if (update.hasPoll()) return "POLL"; // Poll update itself
        if (update.hasMyChatMember()) return "MY_CHAT_MEMBER";
        if (update.hasChatMember()) return "CHAT_MEMBER";
        if (update.hasChatJoinRequest()) return "CHAT_JOIN_REQUEST";
        return "OTHER";
    }
}
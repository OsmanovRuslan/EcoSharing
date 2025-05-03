package ru.ecosharing.telegram_bot_service.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import org.telegram.telegrambots.meta.api.objects.WebhookInfo;
import ru.ecosharing.telegram_bot_service.service.NotificationService;
import ru.ecosharing.telegram_bot_service.service.TelegramBotService;
import ru.ecosharing.telegram_bot_service.dto.CreateInvoiceRequest;
import ru.ecosharing.telegram_bot_service.dto.NotificationRequest;
import ru.ecosharing.telegram_bot_service.dto.WebhookInfoResponse;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/telegram")
public class TelegramController {

    private final TelegramBotService telegramBotService;
    private final NotificationService notificationService;

    @PostMapping("/notify")
    public ResponseEntity<Boolean> sendNotification(@RequestBody NotificationRequest request) {
        log.info("Received notification request for chat {}: Type: {}, AttachApp: {}",
                request.getChatId(), request.getNotificationType(), request.isAttachWebAppButton());
        if (request.getChatId() == null || request.getNotificationType() == null) {
            log.warn("Invalid notification request: chatId or notificationType is null");
            return ResponseEntity.badRequest().body(false);
        }
        boolean result = notificationService.sendNotification(
                request.getChatId(), request.getNotificationType(), request.getParams(), request.isAttachWebAppButton());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/notify/user/{userId}")
    public ResponseEntity<Boolean> sendNotificationByUserId(@PathVariable String userId, @RequestBody NotificationRequest request) {
        log.info("Received notification request for user ID {}: Type: {}", userId, request.getNotificationType());
        if (userId == null || userId.isEmpty() || request.getNotificationType() == null) {
            log.warn("Invalid notification request: userId or notificationType is null/empty");
            return ResponseEntity.badRequest().body(false);
        }
        boolean result = notificationService.sendNotificationByUserId(userId, request.getNotificationType(), request.getParams());
        return ResponseEntity.ok(result);
    }

    /**
     * Создает ссылку на оплату (invoice slug) через Telegram API.
     * Используется Mini App для инициации платежа Stars (XTR).
     */
    @PostMapping("/payments/invoice-link")
    public ResponseEntity<Map<String, String>> createInvoiceLink(@RequestBody CreateInvoiceRequest request) {
        log.info("Received request to create invoice link. UserID(opt): {}, Title: '{}', Payload: '{}', Currency: {}, Amount: {}",
                request.getUserId(), request.getTitle(), request.getPayload(), request.getCurrency(), request.getAmount());

        // --- Валидация запроса ---
        if (request.getTitle() == null || request.getTitle().isEmpty() || request.getTitle().length() > 32) {
            return ResponseEntity.badRequest().body(Map.of("error", "Title is required (max 32 chars)"));
        }
        if (request.getDescription() == null || request.getDescription().isEmpty() || request.getDescription().length() > 255) {
            return ResponseEntity.badRequest().body(Map.of("error", "Description is required (max 255 chars)"));
        }
        if (request.getPayload() == null || request.getPayload().isEmpty() || request.getPayload().getBytes().length > 128 ) {
            return ResponseEntity.badRequest().body(Map.of("error", "Payload is required (max 128 bytes)"));
        }
        if (!"XTR".equalsIgnoreCase(request.getCurrency())) { // Ожидаем только Stars
            return ResponseEntity.badRequest().body(Map.of("error", "Currency must be 'XTR' for Stars payments"));
        }
        if (request.getAmount() == null || request.getAmount() <= 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "Amount (stars) must be positive"));
        }

        try {
            String invoiceLink = telegramBotService.createInvoiceLink(
                    request.getTitle(),
                    request.getDescription(),
                    request.getPayload(), // Payload должен содержать все для идентификации
                    request.getCurrency(), // Будет "XTR"
                    request.getAmount()
            );

            if (invoiceLink != null) {
                log.info("Successfully generated invoice link (slug) for payload '{}'", request.getPayload());
                return ResponseEntity.ok(Map.of("invoiceUrl", invoiceLink));
            } else {
                return ResponseEntity.status(500).body(Map.of("error", "Failed to create invoice link (see service logs)"));
            }
        } catch (Exception e) {
            log.error("Error in createInvoiceLink controller: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", "Internal error: " + e.getMessage()));
        }
    }

    @GetMapping("/webhook/status")
    public ResponseEntity<WebhookInfoResponse> getWebhookInfo() {
        log.info("Received request to get webhook info");
        WebhookInfoResponse response = new WebhookInfoResponse();
        try {
            WebhookInfo webhookInfo = telegramBotService.getWebhookInfo(); // Получаем объект WebhookInfo
            if (webhookInfo != null) {
                response.setSuccess(true);
                // Конвертируем объект WebhookInfo в Map для DTO
                Map<String, Object> infoMap = new HashMap<>();
                infoMap.put("url", webhookInfo.getUrl());
                infoMap.put("has_custom_certificate", webhookInfo.getHasCustomCertificate());
                infoMap.put("pending_update_count", webhookInfo.getPendingUpdatesCount());
                infoMap.put("ip_address", webhookInfo.getIpAddress());
                infoMap.put("last_error_date", webhookInfo.getLastErrorDate());
                infoMap.put("last_error_message", webhookInfo.getLastErrorMessage());
                infoMap.put("last_synchronization_error_date", webhookInfo.getLastSynchronizationErrorDate());
                infoMap.put("max_connections", webhookInfo.getMaxConnections());
                infoMap.put("allowed_updates", webhookInfo.getAllowedUpdates());
                response.setWebhookInfo(infoMap);
            } else {
                response.setSuccess(false);
                response.setError("Failed to retrieve webhook info from Telegram API.");
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error getting webhook info controller level", e);
            response.setSuccess(false);
            response.setError("Internal error getting webhook info: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * Отправляет тестовое сообщение (для отладки).
     * ЗАЩИТИТЕ ЭТОТ ЭНДПОИНТ!
     */
    @PostMapping("/message/send-debug")
    public ResponseEntity<Boolean> sendDebugMessage(
            @RequestParam String chatId,
            @RequestParam String text) {
        log.warn("Received DEBUG request to send message to chatId: {}", chatId);
        boolean result = telegramBotService.sendMessage(chatId, "[DEBUG] " + text);
        return ResponseEntity.ok(result);
    }
}
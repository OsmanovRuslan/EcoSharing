package ru.ecosharing.telegram_bot_service.bot;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.AnswerPreCheckoutQuery;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.payments.PreCheckoutQuery;
import org.telegram.telegrambots.meta.api.objects.payments.SuccessfulPayment;

import ru.ecosharing.telegram_bot_service.client.SubscriptionServiceClient; // Используется для активации
import ru.ecosharing.telegram_bot_service.model.SubscriptionPeriod; // Используется для активации

import java.util.HashMap;
import java.util.Map;

/**
 * Обработчик платежей через Telegram.
 * Сфокусирован на обработке УСПЕШНЫХ платежей в Telegram Stars (XTR).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TelegramPaymentHandler {

    // Оставляем сервис для активации подписки/услуги после оплаты
    private final SubscriptionServiceClient subscriptionServiceClient;
    // private final ProductServiceClient productServiceClient; // Если покупаются товары

    private static final String STARS_CURRENCY = "XTR";

    /**
     * Обрабатывает запрос предварительной проверки платежа (PreCheckoutQuery).
     * ВАЖНО: Для Telegram Stars (XTR) этот метод не вызывается Telegram.
     * Оставляем как заглушку для логирования и на случай будущих изменений.
     */
    public BotApiMethod<?> handlePreCheckoutQuery(PreCheckoutQuery preCheckoutQuery) {
        log.warn("Received PreCheckoutQuery ID: {} for user {} (Currency: {}). This is NOT expected for Telegram Stars (XTR) payments. Approving anyway.",
                preCheckoutQuery.getId(),
                preCheckoutQuery.getFrom().getId(),
                preCheckoutQuery.getCurrency());

        // Всегда подтверждаем, так как валидация Stars происходит на стороне Telegram
        return createSuccessPreCheckoutResponse(preCheckoutQuery.getId());
    }

    /**
     * Обрабатывает успешный платеж (SuccessfulPayment).
     * Ожидаем здесь ТОЛЬКО платежи в Telegram Stars (XTR).
     */
    public BotApiMethod<?> handleSuccessfulPayment(String telegramId, SuccessfulPayment payment) {
        log.info("Processing successful payment from user {}. Currency: {}, Amount: {}, Payload: {}, TelegramPaymentChargeId: {}",
                telegramId,
                payment.getCurrency(),
                payment.getTotalAmount(),
                payment.getInvoicePayload(),
                payment.getTelegramPaymentChargeId()); // Используем ID платежа Telegram

        // 1. Проверяем валюту - ожидаем ТОЛЬКО Stars
        if (!STARS_CURRENCY.equals(payment.getCurrency())) {
            log.error("Received SuccessfulPayment with unexpected currency '{}' from user {}. Expected '{}'. Payload: {}",
                    payment.getCurrency(), telegramId, STARS_CURRENCY, payment.getInvoicePayload());
            return createErrorPaymentMessage(telegramId,
                    "Произошла ошибка: получена оплата в неверной валюте. Пожалуйста, свяжитесь с поддержкой.");
        }

        // 2. Проверяем и парсим payload
        try {
            String payload = payment.getInvoicePayload();
            if (payload == null || payload.isEmpty()) {
                log.error("SuccessfulPayment (Stars) from user {} has empty payload.", telegramId);
                return createErrorPaymentMessage(telegramId,
                        "Платеж Stars получен, но не удалось обработать детали заказа. Свяжитесь с поддержкой.");
            }
            Map<String, String> payloadData = parsePayload(payload);

            // 3. Определяем, что было куплено (из payload) и активируем
            // Пример: покупка подписки за Stars
            if (payloadData.containsKey("period")) {
                return processSubscriptionStarsPayment(telegramId, payment, payloadData);
            } else {
                log.error("Unknown payload structure in SuccessfulPayment (Stars) from user {}: {}", telegramId, payload);
                return createErrorPaymentMessage(telegramId,
                        "Платеж Stars получен, но не удалось определить тип покупки. Свяжитесь с поддержкой.");
            }

        } catch (IllegalArgumentException e) {
            log.error("Invalid payload format in SuccessfulPayment (Stars) from user {}", telegramId, e);
            return createErrorPaymentMessage(telegramId,
                    "Ошибка обработки данных платежа Stars. Пожалуйста, свяжитесь с поддержкой.");
        } catch (Exception e) {
            log.error("Error processing successful Stars payment from user {}", telegramId, e);
            return createErrorPaymentMessage(telegramId,
                    "⚠️ Произошла ошибка при обработке вашего платежа Stars.\n\n" +
                            "Платеж получен, но не удалось завершить заказ. Свяжитесь с поддержкой.");
        }
    }

    // --- Обработка конкретных покупок за Stars ---

    private BotApiMethod<?> processSubscriptionStarsPayment(String telegramId, SuccessfulPayment payment, Map<String, String> payloadData) {
        SubscriptionPeriod period;
        try {
            period = SubscriptionPeriod.valueOf(payloadData.get("period").toUpperCase());
        } catch (IllegalArgumentException e) {
            log.error("Invalid 'period' value in payload for Stars payment from user {}: {}", telegramId, payloadData.get("period"));
            return createErrorPaymentMessage(telegramId, "Ошибка в данных заказа (период). Свяжитесь с поддержкой.");
        }

        String userId = payloadData.getOrDefault("user_id", telegramId);
        int starsAmount = payment.getTotalAmount(); // Количество звезд

        log.info("Activating subscription '{}' for user ID: {} (Telegram ID: {}) using {} Stars.",
                period, userId, telegramId, starsAmount);

        // Активируем подписку через сервис подписок
        boolean subscriptionActivated = subscriptionServiceClient.activateSubscription(
                userId,
                period,
                starsAmount, // Передаем количество звезд
                STARS_CURRENCY, // Передаем валюту XTR
                payment.getTelegramPaymentChargeId() // ID платежа Telegram
        );

        if (!subscriptionActivated) {
            log.error("Failed to activate subscription {} for user {} after Stars payment.", period, userId);
            return createErrorPaymentMessage(telegramId,
                    "Ваш платеж Stars получен, но произошла ошибка при активации подписки. Мы уже разбираемся.");
        }

        // Формируем сообщение об успехе
        int days = getSubscriptionDays(period);
        String messageText = String.format(
                "✅ <b>Подписка успешно оплачена!</b>\n\n" +
                        "Период: <b>%s</b>\n" +
                        "Срок действия: <b>%d %s</b>\n" +
                        "Сумма: %d ⭐️\n\n" + // Показываем звезды
                        "Спасибо за использование нашего сервиса!",
                getPeriodName(period),
                days,
                getDaysText(days),
                starsAmount);

        SendMessage successMessage = new SendMessage(telegramId, messageText);
        successMessage.setParseMode("HTML");
        return successMessage;
    }


    // --- Вспомогательные методы ---

    private AnswerPreCheckoutQuery createSuccessPreCheckoutResponse(String preCheckoutQueryId) {
        AnswerPreCheckoutQuery answer = new AnswerPreCheckoutQuery();
        answer.setPreCheckoutQueryId(preCheckoutQueryId);
        answer.setOk(true);
        return answer;
    }

    private AnswerPreCheckoutQuery createFailedPreCheckoutResponse(String preCheckoutQueryId, String errorMessage) {
        // Этот метод практически не будет использоваться при оплате только Stars
        AnswerPreCheckoutQuery answer = new AnswerPreCheckoutQuery();
        answer.setPreCheckoutQueryId(preCheckoutQueryId);
        answer.setOk(false);
        answer.setErrorMessage(errorMessage);
        return answer;
    }

    private SendMessage createErrorPaymentMessage(String chatId, String text) {
        SendMessage errorMessage = new SendMessage(chatId, text);
        errorMessage.setParseMode("HTML");
        return errorMessage;
    }

    private Map<String, String> parsePayload(String payload) {
        Map<String, String> result = new HashMap<>();
        if (payload == null || payload.isEmpty()) return result;
        try {
            String[] pairs = payload.split("&");
            for (String pair : pairs) {
                String[] keyValue = pair.split("=", 2);
                if (keyValue.length == 2 && !keyValue[0].isEmpty()) {
                    result.put(keyValue[0], keyValue[1]);
                } else if (keyValue.length == 1 && !keyValue[0].isEmpty()) {
                    result.put(keyValue[0], "");
                }
            }
        } catch (Exception e) { log.error("Failed to parse payload: {}", payload, e); }
        return result;
    }

    // Методы для описания подписки (если payload содержит 'period')
    private int getSubscriptionDays(SubscriptionPeriod period) {
        return switch (period) {
            case WEEKLY -> 7;
            case QUARTERLY -> 90;
            case YEARLY -> 365;
            default -> 30; // MONTHLY
        };
    }

    private String getPeriodName(SubscriptionPeriod period) {
        return switch (period) {
            case WEEKLY -> "Недельная";
            case MONTHLY -> "Месячная";
            case QUARTERLY -> "3-х месячная";
            case YEARLY -> "Годовая";
        };
    }

    private String getDaysText(int days) {
        int lastDigit = days % 10;
        int lastTwoDigits = days % 100;
        if (lastTwoDigits >= 11 && lastTwoDigits <= 19) return "дней";
        return switch (lastDigit) {
            case 1 -> "день";
            case 2, 3, 4 -> "дня";
            default -> "дней";
        };
    }
}
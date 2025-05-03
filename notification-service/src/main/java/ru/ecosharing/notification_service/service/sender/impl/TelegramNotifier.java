package ru.ecosharing.notification_service.service.sender.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import ru.ecosharing.notification_service.dto.UserProfileDto;
import ru.ecosharing.notification_service.dto.kafka.TelegramSendMessageKafkaDto;
import ru.ecosharing.notification_service.exception.NotificationSendException;
import ru.ecosharing.notification_service.model.enums.NotificationChannel;
import ru.ecosharing.notification_service.service.sender.Notifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Реализация отправителя уведомлений в Telegram через Kafka.
 * Формирует DTO и отправляет его в Kafka топик, предназначенный для telegram-bot-service.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramNotifier implements Notifier {

    private final KafkaTemplate<String, TelegramSendMessageKafkaDto> telegramKafkaTemplate;

    @Value("${kafka.topic.telegram-send-requests}")
    private String telegramSendTopic;

    @Value("${app.telegram.webapp-url:}")
    private String baseWebAppUrl;


    @Override
    public NotificationChannel getChannel() {
        return NotificationChannel.TELEGRAM;
    }

    /**
     * Асинхронно отправляет запрос на отправку Telegram сообщения в Kafka.
     *
     * @param recipient Профиль получателя (нужен telegramId).
     * @param subject Тема (не используется для Telegram Push).
     * @param formattedText HTML-текст сообщения.
     * @param params Исходные параметры (для возможной кнопки).
     * @param targetUrl Опциональный URL для deeplink в WebApp кнопке.
     * @param attachWebAppButton Флаг: прикреплять ли кнопку WebApp.
     */
    @Async
    @Override
    public void send(UserProfileDto recipient,
                     String subject,
                     String formattedText,
                     Map<String, String> params,
                     String targetUrl,
                     boolean attachWebAppButton) {

        String recipientTelegramId = recipient.getTelegramId();
        UUID userId = recipient.getUserId();

        // 1. Проверка наличия Telegram ID
        if (recipientTelegramId == null || recipientTelegramId.isBlank()) {
            log.warn("TelegramNotifier: Невозможно отправить уведомление - telegramId отсутствует для пользователя ID {}", userId);
            return;
        }

        log.debug("TelegramNotifier: Подготовка Kafka сообщения для TG ID {}", recipientTelegramId);

        // 2. Создаем DTO для отправки в Kafka
        TelegramSendMessageKafkaDto kafkaDto = new TelegramSendMessageKafkaDto();
        kafkaDto.setChatId(recipientTelegramId);
        kafkaDto.setText(formattedText);
        kafkaDto.setParseMode("HTML");

        // 3. Создаем клавиатуру (кнопку WebApp), если нужно
        if (attachWebAppButton) {
            // Проверяем, задан ли базовый URL для WebApp
            if (baseWebAppUrl == null || baseWebAppUrl.isBlank()) {
                log.warn("Невозможно прикрепить кнопку WebApp: свойство 'app.telegram.webapp-url' не настроено.");
            } else {
                kafkaDto.setReplyMarkup(createKafkaWebAppKeyboard(params, targetUrl));
            }
        }

        // 4. Асинхронно отправляем сообщение в Kafka
        try {
            // Используем chatId как ключ Kafka сообщения для партиционирования
            // Сообщения для одного пользователя попадут в одну партицию (сохраняет порядок для пользователя)
            CompletableFuture<SendResult<String, TelegramSendMessageKafkaDto>> future =
                    telegramKafkaTemplate.send(telegramSendTopic, recipientTelegramId, kafkaDto);

            // Добавляем обработчик результата отправки (асинхронный)
            future.whenComplete((result, ex) -> {
                if (ex == null) {
                    // Успешно отправлено в Kafka
                    log.info("TelegramNotifier: Запрос на отправку сообщения для TG ID {} успешно отправлен в Kafka (Topic: {}, Partition: {}, Offset: {})",
                            recipientTelegramId,
                            result.getRecordMetadata().topic(),
                            result.getRecordMetadata().partition(),
                            result.getRecordMetadata().offset());
                } else {
                    // Ошибка при отправке в Kafka
                    log.error("TelegramNotifier: Ошибка отправки запроса для TG ID {} в Kafka топик {}: {}",
                            recipientTelegramId, telegramSendTopic, ex.getMessage(), ex);
                    throw new NotificationSendException("Ошибка отправки сообщения в Kafka для Telegram", ex, getChannel());
                }
            });

        } catch (Exception e) {
            // Ошибка на этапе ДО отправки в Kafka (например, при сериализации)
            log.error("TelegramNotifier: Исключение при подготовке к отправке в Kafka для TG ID {}: {}",
                    recipientTelegramId, e.getMessage(), e);
            throw new NotificationSendException("Ошибка подготовки сообщения Kafka для Telegram", e, getChannel());
        }
    }

    /**
     * Создает DTO для Inline клавиатуры с кнопкой WebApp для Kafka DTO.
     * @param params Параметры уведомления (могут содержать screenPath).
     * @param targetUrl Явно заданный URL/путь для кнопки.
     * @return DTO клавиатуры или null, если URL не удалось сформировать.
     */
    private TelegramSendMessageKafkaDto.InlineKeyboardMarkupDto createKafkaWebAppKeyboard(Map<String, String> params, String targetUrl) {
        List<List<TelegramSendMessageKafkaDto.InlineKeyboardButtonDto>> keyboard = new ArrayList<>();
        List<TelegramSendMessageKafkaDto.InlineKeyboardButtonDto> row = new ArrayList<>();

        TelegramSendMessageKafkaDto.InlineKeyboardButtonDto appButton = new TelegramSendMessageKafkaDto.InlineKeyboardButtonDto();
        appButton.setText("🌱 Открыть EcoSharing");

        String finalUrl = baseWebAppUrl;
        // Определяем путь внутри WebApp (приоритет у targetUrl)
        String screenPath = targetUrl != null ? targetUrl : (params != null ? params.get("screenPath") : null);
        if (screenPath != null && !screenPath.isBlank()) {
            // Убираем возможный слэш в начале и добавляем параметр startapp
            screenPath = screenPath.startsWith("/") ? screenPath.substring(1) : screenPath;
            // Добавляем параметр безопасно, проверяя наличие '?'
            if (finalUrl.contains("?")) {
                finalUrl += "&startapp=" + screenPath;
            } else {
                finalUrl += "?startapp=" + screenPath;
            }
        }
        appButton.setUrl(finalUrl);
        appButton.setCallbackData(null);

        row.add(appButton);
        keyboard.add(row);

        TelegramSendMessageKafkaDto.InlineKeyboardMarkupDto keyboardMarkupDto = new TelegramSendMessageKafkaDto.InlineKeyboardMarkupDto();
        keyboardMarkupDto.setInlineKeyboard(keyboard);
        return keyboardMarkupDto;
    }
}
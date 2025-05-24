package ru.ecosharing.telegram_bot_service.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import ru.ecosharing.telegram_bot_service.dto.TelegramSendMessageKafkaDto;
import ru.ecosharing.telegram_bot_service.service.TelegramBotService;

import java.util.ArrayList;
import java.util.List;

/**
 * Kafka Consumer для прослушивания команд на отправку Telegram уведомлений
 * из топика 'telegram-send-requests'.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationCommandConsumer { // Переименован для ясности

    private final TelegramBotService telegramBotService;

    @KafkaListener(
            topics = "${kafka.topic.telegram-send-requests}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "telegramSendCommandKafkaListenerContainerFactory" // Используем фабрику для этого DTO
    )
    public void consumeTelegramSendCommand(
            @Payload(required = false) TelegramSendMessageKafkaDto requestDto,
            ConsumerRecord<String, TelegramSendMessageKafkaDto> record) {

        if (requestDto == null) {
            log.error("Получен null TelegramSendMessageKafkaDto из Kafka (ошибка десериализации?). Record: {}", record);
            return;
        }

        log.info("Получена команда на отправку Telegram сообщения для chatId: {} из Kafka. Text: ~{} символов",
                requestDto.getChatId(), requestDto.getText() != null ? requestDto.getText().length() : 0);
        log.debug("Полные данные команды из Kafka: {}", requestDto);

        if (requestDto.getChatId() == null || requestDto.getChatId().isBlank()) {
            log.error("Отсутствует chatId в Kafka DTO для отправки Telegram сообщения. Update: {}", requestDto);
            return;
        }
        if (requestDto.getText() == null || requestDto.getText().isBlank()) {
            log.error("Отсутствует текст в Kafka DTO для отправки Telegram сообщения. ChatId: {}", requestDto.getChatId());
            return;
        }

        try {
            InlineKeyboardMarkup keyboardMarkup = null;
            if (requestDto.getReplyMarkup() != null) {
                keyboardMarkup = convertKafkaKeyboardToApi(requestDto.getReplyMarkup());
            }

            // Напрямую используем данные из DTO для отправки
            boolean sent = telegramBotService.sendMessage(
                    requestDto.getChatId(),
                    requestDto.getText(),
                    keyboardMarkup
            );

            if (sent) {
                log.info("Сообщение для chatId {} успешно отправлено через TelegramBotService.", requestDto.getChatId());
            } else {
                log.warn("TelegramBotService сообщил о неудаче отправки для chatId {}.", requestDto.getChatId());
            }

        } catch (Exception e) {
            log.error("Ошибка при обработке команды на отправку из Kafka для chatId {}: {}",
                    requestDto.getChatId(), e.getMessage(), e);
        }
    }

    // Метод convertKafkaKeyboardToApi остается таким же, как я приводил ранее
    private InlineKeyboardMarkup convertKafkaKeyboardToApi(TelegramSendMessageKafkaDto.InlineKeyboardMarkupDto kafkaKeyboard) {
        if (kafkaKeyboard == null || kafkaKeyboard.getInlineKeyboard() == null) return null;
        InlineKeyboardMarkup apiKeyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> apiRows = new ArrayList<>();
        for (List<TelegramSendMessageKafkaDto.InlineKeyboardButtonDto> kafkaRow : kafkaKeyboard.getInlineKeyboard()) {
            List<InlineKeyboardButton> apiRow = new ArrayList<>();
            for (TelegramSendMessageKafkaDto.InlineKeyboardButtonDto kafkaButton : kafkaRow) {
                InlineKeyboardButton apiButton = new InlineKeyboardButton();
                apiButton.setText(kafkaButton.getText());
                if (kafkaButton.getUrl() != null && !kafkaButton.getUrl().isBlank()) {
                    apiButton.setUrl(kafkaButton.getUrl());
                } else if (kafkaButton.getCallbackData() != null && !kafkaButton.getCallbackData().isBlank()) {
                    apiButton.setCallbackData(kafkaButton.getCallbackData());
                } else { continue; }
                apiRow.add(apiButton);
            }
            if (!apiRow.isEmpty()) apiRows.add(apiRow);
        }
        if (!apiRows.isEmpty()) { apiKeyboard.setKeyboard(apiRows); return apiKeyboard; }
        return null;
    }
}
package ru.ecosharing.telegram_bot_service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;


@Data
@NoArgsConstructor
public class TelegramSendMessageKafkaDto {

    private String chatId;

    private String text;

    private String parseMode = "HTML";

    private InlineKeyboardMarkupDto replyMarkup;

    @Data @NoArgsConstructor
    public static class InlineKeyboardMarkupDto {

        @JsonProperty("inline_keyboard")
        private List<List<InlineKeyboardButtonDto>> inlineKeyboard;

    }

    @Data @NoArgsConstructor
    public static class InlineKeyboardButtonDto {

        private String text;

        private String url;

        @JsonProperty("callback_data")
        private String callbackData;

    }
}
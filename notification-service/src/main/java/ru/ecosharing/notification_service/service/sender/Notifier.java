package ru.ecosharing.notification_service.service.sender;

import ru.ecosharing.notification_service.dto.UserProfileDto;
import ru.ecosharing.notification_service.model.enums.NotificationChannel;

import java.util.Map;

/**
 * Общий интерфейс для отправителей уведомлений (Notifier).
 * Каждая реализация отвечает за отправку уведомлений через конкретный канал (Email, Telegram и т.д.).
 * Методы отправки должны быть асинхронными (@Async), чтобы не блокировать основной поток обработки.
 */
public interface Notifier {

    /**
     * Возвращает канал доставки, за который отвечает данный Notifier.
     * @return NotificationChannel.
     */
    NotificationChannel getChannel();

    /**
     * Асинхронно отправляет уведомление через свой канал.
     * Реализация должна быть помечена аннотацией @Async.
     *
     * @param recipient Профиль получателя (содержит email, telegramId и т.д.).
     * @param subject Тема/заголовок уведомления (используется в Email).
     * @param formattedText Отформатированный текст уведомления, готовый к отправке.
     * @param params Карта с исходными параметрами (может понадобиться для генерации кнопок, ссылок).
     * @param targetUrl Опциональный URL для кнопок/ссылок в уведомлении.
     * @param attachWebAppButton Флаг, актуальный для Telegram, указывающий на необходимость добавить кнопку WebApp.
     */
    void send(UserProfileDto recipient,
              String subject,
              String formattedText,
              Map<String, String> params,
              String targetUrl,
              boolean attachWebAppButton);
}
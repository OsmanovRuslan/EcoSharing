package ru.ecosharing.notification_service.service.sender.impl;

import jakarta.mail.MessagingException; // Используем jakarta.mail для Boot 3
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async; // Для асинхронной отправки
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import ru.ecosharing.notification_service.config.AppProperties; // Для настроек отправителя
import ru.ecosharing.notification_service.dto.UserProfileDto; // Внутренний DTO
import ru.ecosharing.notification_service.exception.NotificationSendException; // Наше исключение
import ru.ecosharing.notification_service.model.enums.NotificationChannel;
import ru.ecosharing.notification_service.service.sender.Notifier;

import java.io.UnsupportedEncodingException;
import java.util.Map;
import java.util.UUID;

/**
 * Реализация отправителя уведомлений через Email.
 * Использует Spring Mail Sender для отправки HTML-писем.
 */
@Slf4j
@Service // Помечаем как Spring Service бин
@RequiredArgsConstructor // Внедряем зависимости через конструктор
public class EmailNotifier implements Notifier {

    private final JavaMailSender mailSender; // Стандартный бин Spring для отправки почты
    private final AppProperties appProperties; // Свойства приложения (адрес/имя отправителя)

    /**
     * Возвращает канал, за который отвечает этот отправитель.
     * @return NotificationChannel.EMAIL
     */
    @Override
    public NotificationChannel getChannel() {
        return NotificationChannel.EMAIL;
    }

    /**
     * Асинхронно отправляет Email уведомление.
     * Выполняется в отдельном потоке из пула, настроенного в AsyncConfig.
     *
     * @param recipient Профиль получателя (нужен email).
     * @param subject Тема письма.
     * @param formattedText HTML-текст письма.
     * @param params Параметры (не используются напрямую при отправке, но передаются).
     * @param targetUrl URL для возможной ссылки "Подробнее" (не используется в этой реализации).
     * @param attachWebAppButton Флаг (не используется для Email).
     * @throws NotificationSendException если произошла ошибка при отправке.
     */
    @Async
    @Override
    public void send(UserProfileDto recipient,
                     String subject,
                     String formattedText,
                     Map<String, String> params,
                     String targetUrl,
                     boolean attachWebAppButton) {

        String recipientEmail = recipient.getEmail();
        UUID userId = recipient.getUserId();

        // 1. Проверка наличия email адреса
        if (recipientEmail == null || recipientEmail.isBlank()) {
            log.warn("EmailNotifier: Невозможно отправить Email уведомление - email отсутствует для пользователя ID {}", userId);
            return;
        }

        log.debug("EmailNotifier: Попытка отправки Email на адрес {} для пользователя ID {}", recipientEmail, userId);

        try {
            // 2. Создаем MimeMessage для поддержки HTML и вложений
            MimeMessage message = mailSender.createMimeMessage();

            // 3. Используем MimeMessageHelper для удобной настройки сообщения
            //    true - multipart message (для HTML и/или вложений)
            //    "UTF-8" - кодировка письма
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            // 4. Устанавливаем отправителя (из настроек)
            try {
                // Пытаемся установить с именем отправителя
                helper.setFrom(appProperties.getMailFrom(), appProperties.getMailSenderName());
            } catch (UnsupportedEncodingException e) {
                log.warn("Не удалось установить имя отправителя '{}', используем только email '{}': {}",
                        appProperties.getMailSenderName(), appProperties.getMailFrom(), e.getMessage());
                helper.setFrom(appProperties.getMailFrom());
            } catch (MessagingException me) {
                // Обработка других ошибок при установке отправителя
                log.error("Ошибка установки отправителя Email: {}", me.getMessage(), me);
                throw new NotificationSendException("Ошибка настройки отправителя Email", me, getChannel());
            }

            // 5. Устанавливаем получателя
            helper.setTo(recipientEmail);

            // 6. Устанавливаем тему (с дефолтным значением)
            helper.setSubject(subject != null && !subject.isBlank() ? subject : "Уведомление от EcoSharing");

            // 7. Устанавливаем тело письма как HTML
            // TODO: Рассмотреть возможность добавления plain text версии для старых клиентов ???
            String emailBody = buildEmailBody(formattedText, targetUrl);
            helper.setText(emailBody, true);

            // 8. Отправляем сообщение
            mailSender.send(message);
            log.info("EmailNotifier: Email уведомление успешно отправлено на {} для пользователя ID {}", recipientEmail, userId);

        } catch (MessagingException e) {
            // Ошибки, связанные с созданием или отправкой сообщения (например, неверный адрес, проблемы SMTP)
            log.error("EmailNotifier: Ошибка при отправке Email на {} для пользователя ID {}: {}",
                    recipientEmail, userId, e.getMessage(), e);
            throw new NotificationSendException("Ошибка отправки Email: " + e.getMessage(), e, getChannel());
        } catch (Exception e) {
            // Другие непредвиденные ошибки
            log.error("EmailNotifier: Неожиданная ошибка при отправке Email на {} для пользователя ID {}: {}",
                    recipientEmail, userId, e.getMessage(), e);
            throw new NotificationSendException("Непредвиденная ошибка при отправке Email", e, getChannel());
        }
    }

    /**
     * Собирает полное тело HTML-письма, добавляя кнопку "Подробнее", если есть targetUrl.
     * @param mainContent Основной HTML-контент письма.
     * @param targetUrl URL для кнопки "Подробнее".
     * @return Полный HTML код для тела письма.
     */
    private String buildEmailBody(String mainContent, String targetUrl) {
        StringBuilder bodyBuilder = new StringBuilder();
        bodyBuilder.append("<html><body>");
        bodyBuilder.append(mainContent);

        // Добавляем кнопку/ссылку, если targetUrl передан и не пуст
        if (StringUtils.hasText(targetUrl)) {
            log.debug("Добавление кнопки 'Подробнее' с URL: {}", targetUrl);
            bodyBuilder.append("<br/><br/>");
            bodyBuilder.append(String.format(
                    "<a href=\"%s\" style=\"background-color: #4CAF50; color: white; padding: 10px 20px; text-align: center; text-decoration: none; display: inline-block; border-radius: 5px; font-weight: bold;\">Подробнее</a>",
                    targetUrl
            ));
            bodyBuilder.append("<br/><br/>");
        }

        bodyBuilder.append("<hr/>");
        bodyBuilder.append("<p style=\"font-size: small; color: grey;\">Это автоматическое уведомление от сервиса EcoSharing. Отвечать на него не нужно.</p>");

        bodyBuilder.append("</body></html>");
        return bodyBuilder.toString();
    }
}
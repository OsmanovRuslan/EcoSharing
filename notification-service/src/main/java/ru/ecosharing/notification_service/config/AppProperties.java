package ru.ecosharing.notification_service.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

/**
 * Класс для хранения кастомных свойств приложения Notification Service,
 * читаемых из application.yml с префиксом "app".
 */
@Configuration
@ConfigurationProperties(prefix = "app") // Указываем префикс свойств
@Data
@Validated
public class AppProperties {

    // Вложенные свойства для Email
    @NestedConfigurationProperty // Указываем, что это вложенный объект конфигурации
    @Valid // Включаем валидацию полей внутри Mail
    private Mail mail = new Mail();

    // --- Вложенные классы для группировки ---

    @Data
    public static class Mail {
        @NotBlank(message = "app.mail.from не может быть пустым")
        private String from; // Email адрес отправителя

        private String senderName = "EcoSharing"; // Имя отправителя по умолчанию
    }

    // --- Удобные геттеры для прямого доступа ---
    public String getMailFrom() {
        return mail.getFrom();
    }
    public String getMailSenderName() {
        return mail.getSenderName();
    }

}

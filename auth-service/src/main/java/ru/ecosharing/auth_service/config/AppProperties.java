package ru.ecosharing.auth_service.config;


import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Configuration
@ConfigurationProperties
@Data
@Validated
public class AppProperties {

    @NestedConfigurationProperty // Указываем, что это вложенная конфигурация
    private Jwt jwt = new Jwt();

    @NestedConfigurationProperty
    private Admin admin = new Admin();

    @NestedConfigurationProperty
    private Moderator moderator = new Moderator();

    @NestedConfigurationProperty
    private Telegram telegram = new Telegram();


    @Data // Вложенный класс для JWT
    public static class Jwt {
        @NotBlank(message = "JWT secret не может быть пустым")
        private String secret;

        @NotNull(message = "Время жизни access токена JWT не может быть null")
        private Long accessExpirationMs;

        @NotNull(message = "Время жизни refresh токена JWT не может быть null")
        private Long refreshExpirationMs;
    }

    @Data // Вложенный класс для Admin
    public static class Admin {
        @NotBlank(message = "Секретный пароль администратора не может быть пустым")
        private String secretPassword;
    }

    @Data // Вложенный класс для Moderator
    public static class Moderator {
        @NotBlank(message = "Секретный пароль модератора не может быть пустым")
        private String secretPassword;
    }

    @Data // Вложенный класс для Telegram
    public static class Telegram {
        @NotBlank(message = "Токен Telegram бота не может быть пустым")
        private String botToken;
    }

    // Добавляем для удобного доступа к вложенным свойствам
    public String getJwtSecret() { return jwt.getSecret(); }
    public Long getJwtAccessExpirationMs() { return jwt.getAccessExpirationMs(); }
    public Long getJwtRefreshExpirationMs() { return jwt.getRefreshExpirationMs(); }
    public String getAdminSecretPassword() { return admin.getSecretPassword(); }
    public String getModeratorSecretPassword() { return moderator.getSecretPassword(); }
    public String getTelegramBotToken() { return telegram.getBotToken(); }
}
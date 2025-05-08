package ru.ecosharing.notification_service.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Configuration
@ConfigurationProperties(prefix = "jwt")
@Data
@Validated
public class JwtProperties {

    @NotBlank(message = "JWT secret не может быть пустым")
    private String secret;
}
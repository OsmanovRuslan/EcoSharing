package ru.ecosharing.user_service.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.NotBlank;

@Configuration
@ConfigurationProperties(prefix = "jwt")
@Data
@Validated
public class JwtProperties {

    @NotBlank(message = "JWT secret не может быть пустым")
    private String secret;
}
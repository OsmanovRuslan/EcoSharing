package ru.ecosharing.telegram_webhook_gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.filter.CommonsRequestLoggingFilter;

@Configuration
public class WebConfig {

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    // Бин для логирования входящих запросов (полезно для отладки вебхуков)
    @Bean
    public CommonsRequestLoggingFilter requestLoggingFilter() {
        CommonsRequestLoggingFilter loggingFilter = new CommonsRequestLoggingFilter();
        loggingFilter.setIncludeClientInfo(true);
        loggingFilter.setIncludeQueryString(true);
        loggingFilter.setIncludePayload(true);
        loggingFilter.setMaxPayloadLength(10000); // Ограничьте размер логгируемого payload
        loggingFilter.setIncludeHeaders(true); // Включаем заголовки для отладки Secret Token
        loggingFilter.setAfterMessagePrefix("REQUEST DATA : ");
        return loggingFilter;
    }
}
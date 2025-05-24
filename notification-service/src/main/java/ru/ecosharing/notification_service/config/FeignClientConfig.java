package ru.ecosharing.notification_service.config;
import feign.codec.Decoder;
import feign.codec.Encoder;
import feign.jackson.JacksonDecoder; // Или GsonDecoder
import feign.jackson.JacksonEncoder; // Или GsonEncoder
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

public class FeignClientConfig {

    @Bean
    public Decoder feignDecoder() {
        return new JacksonDecoder();
    }

    @Bean
    public Encoder feignEncoder() {
        return new JacksonEncoder();
    }
}
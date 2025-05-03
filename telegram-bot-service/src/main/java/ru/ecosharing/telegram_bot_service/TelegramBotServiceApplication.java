package ru.ecosharing.telegram_bot_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients
public class TelegramBotServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(TelegramBotServiceApplication.class, args);
	}

}

package ru.ecosharing.user_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import net.kaczmarzyk.spring.data.jpa.web.SpecificationArgumentResolver; // Импорт для Resolver'а
import org.springframework.web.method.support.HandlerMethodArgumentResolver; // Импорт для Resolver'а
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer; // Импорт для Resolver'а

import java.util.List; // Импорт для Resolver'а


@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients
public class UserServiceApplication implements WebMvcConfigurer { // Реализуем интерфейс для Resolver'а

	public static void main(String[] args) {
		SpringApplication.run(UserServiceApplication.class, args);
	}

	// Регистрируем SpecificationArgumentResolver для удобной работы со спецификациями в контроллерах
	@Override
	public void addArgumentResolvers(List<HandlerMethodArgumentResolver> argumentResolvers) {
		argumentResolvers.add(new SpecificationArgumentResolver());
	}
}

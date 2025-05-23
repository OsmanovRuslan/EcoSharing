package ru.ecosharing.listing_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class ListingServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(ListingServiceApplication.class, args);
	}

}

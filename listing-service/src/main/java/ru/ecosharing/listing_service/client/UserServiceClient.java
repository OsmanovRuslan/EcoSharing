package ru.ecosharing.listing_service.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity; // Используем ResponseEntity для большей гибкости
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import ru.ecosharing.listing_service.dto.client.PublicUserProfileResponse; // DTO, который мы ожидаем от User Service

import java.util.UUID;


@FeignClient(name = "user-service")
public interface UserServiceClient {

    /**
     * Получает публичную информацию о пользователе по его ID.
     * Эндпоинт и DTO ответа должны существовать в User Service.
     *
     * @param userId ID пользователя.
     * @return ResponseEntity с PublicUserProfileResponse (или аналогом из User Service).
     */
    @GetMapping("/api/users/{userId}/public") // Используем существующий эндпоинт
    ResponseEntity<PublicUserProfileResponse> getPublicUserProfile(@PathVariable("userId") UUID userId);

}
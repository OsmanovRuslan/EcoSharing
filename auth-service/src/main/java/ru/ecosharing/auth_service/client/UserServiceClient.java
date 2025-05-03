package ru.ecosharing.auth_service.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import ru.ecosharing.auth_service.dto.request.AvailabilityCheckRequest;
import ru.ecosharing.auth_service.dto.request.CreateUserProfileRequest;
import ru.ecosharing.auth_service.dto.response.AvailabilityCheckResponse;
import ru.ecosharing.auth_service.dto.response.UserCredentialsResponse;

import java.util.UUID;

/**
 * Feign клиент для взаимодействия с User Service.
 * Имя 'user-service' должно совпадать с spring.application.name соответствующего сервиса
 * или быть настроено в Service Discovery.
 * URL можно указать явно: url = "${user-service.url}"
 */
@FeignClient(name = "user-service")
public interface UserServiceClient {

    /**
     * Запрашивает у User Service ID, username и статус пользователя по логину (username или email).
     * Используется UserDetailsServiceImpl для получения данных при аутентификации.
     * @param login Логин (username или email).
     * @return ResponseEntity с UserCredentialsResponse или 404.
     */
    @GetMapping("/api/internal/credentials/by-login") // Используем внутренний эндпоинт
    ResponseEntity<UserCredentialsResponse> findUserByLogin(@RequestParam("login") String login);

    /**
     * Запрашивает у User Service ID, username и статус пользователя по его userId.
     * Может использоваться при обновлении токена для получения актуального username.
     * @param userId ID пользователя.
     * @return ResponseEntity с UserCredentialsResponse или 404.
     */
    @GetMapping("/api/internal/credentials/by-id") // Используем внутренний эндпоинт
    ResponseEntity<UserCredentialsResponse> findUserById(@RequestParam("userId") UUID userId);


    /**
     * Проверяет в User Service доступность username и email.
     * Используется при регистрации.
     * @param request DTO с username и email для проверки.
     * @return ResponseEntity с AvailabilityCheckResponse.
     */
    @PostMapping("/api/internal/check-availability") // Используем внутренний эндпоинт
    ResponseEntity<AvailabilityCheckResponse> checkAvailability(@RequestBody AvailabilityCheckRequest request);

    /**
     * Отправляет запрос в User Service на создание профиля пользователя.
     * Вызывается после успешного создания учетных данных в Auth Service.
     * @param request DTO с данными для создания профиля.
     * @return ResponseEntity<Void> (ожидается 201 Created или ошибка).
     */
    @PostMapping("/api/internal/users") // Используем внутренний эндпоинт
    ResponseEntity<Void> createUserProfile(@RequestBody CreateUserProfileRequest request);
}
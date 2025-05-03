package ru.ecosharing.auth_service.controller;

import jakarta.validation.Valid; // Для валидации DTO
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import ru.ecosharing.auth_service.dto.request.*;
import ru.ecosharing.auth_service.dto.response.JwtResponse;
import ru.ecosharing.auth_service.dto.telegram.TelegramAuthRequest;
import ru.ecosharing.auth_service.dto.telegram.TelegramAuthResult;
import ru.ecosharing.auth_service.dto.telegram.TelegramLoginRequest;
import ru.ecosharing.auth_service.dto.telegram.TelegramRegisterRequest;
import ru.ecosharing.auth_service.security.JwtTokenProvider; // Нужен для извлечения userId из токена при logout
import ru.ecosharing.auth_service.service.AuthService;
import ru.ecosharing.auth_service.service.TelegramAuthService;

import java.security.Principal; // Для получения информации об аутентифицированном пользователе
import java.util.UUID;

/**
 * REST контроллер для обработки запросов аутентификации, регистрации,
 * обновления токенов и операций, связанных с Telegram.
 */
@Slf4j // Логгер Lombok
@RestController // Объявляет класс как REST контроллер
@RequestMapping("/api/auth") // Базовый путь для всех эндпоинтов контроллера
@RequiredArgsConstructor // Генерирует конструктор для внедрения зависимостей
public class AuthController {

    // --- Зависимости ---
    private final AuthService authService; // Сервис стандартной аутентификации/регистрации
    private final TelegramAuthService telegramAuthService; // Сервис для Telegram операций
    private final JwtTokenProvider jwtTokenProvider; // Провайдер JWT для извлечения данных из токена

    // --- Эндпоинты стандартной аутентификации/регистрации ---

    /**
     * Обрабатывает запрос на вход пользователя по логину и паролю.
     * @param loginRequest DTO с логином и паролем.
     * @return ResponseEntity с JwtResponse при успехе или ошибку.
     */
    @PostMapping("/login")
    public ResponseEntity<JwtResponse> login(@Valid @RequestBody LoginRequest loginRequest) {
        log.info("POST /api/auth/login - Попытка входа для логина: {}", loginRequest.getLogin());
        // Делегируем логику в AuthService
        JwtResponse jwtResponse = authService.login(loginRequest);
        // Возвращаем токены и статус 200 OK
        return ResponseEntity.ok(jwtResponse);
    }

    /**
     * Обрабатывает запрос на регистрацию нового пользователя.
     * @param registerRequest DTO с данными для регистрации.
     * @return ResponseEntity с JwtResponse при успехе или ошибку.
     */
    @PostMapping("/register")
    public ResponseEntity<JwtResponse> register(@Valid @RequestBody RegisterRequest registerRequest) {
        log.info("POST /api/auth/register - Попытка регистрации пользователя: {}", registerRequest.getUsername());
        // Делегируем логику в AuthService
        JwtResponse jwtResponse = authService.register(registerRequest);
        // Возвращаем токены и статус 201 Created, т.к. ресурс был создан
        return ResponseEntity.status(HttpStatus.CREATED).body(jwtResponse);
    }

    /**
     * Обрабатывает запрос на обновление пары токенов с использованием refresh токена.
     * @param refreshTokenRequest DTO с refresh токеном.
     * @return ResponseEntity с новым JwtResponse при успехе или ошибку.
     */
    @PostMapping("/refresh")
    public ResponseEntity<JwtResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest refreshTokenRequest) {
        log.info("POST /api/auth/refresh - Запрос на обновление токена.");
        // Делегируем логику в AuthService
        JwtResponse jwtResponse = authService.refreshToken(refreshTokenRequest);
        // Возвращаем новые токены и статус 200 OK
        return ResponseEntity.ok(jwtResponse);
    }

    /**
     * Обрабатывает запрос на выход пользователя из системы.
     * Требует наличия валидного access токена в заголовке Authorization.
     * @param principal Объект Principal, представляющий аутентифицированного пользователя (внедряется Spring Security).
     * @return ResponseEntity<Void> со статусом 200 OK при успехе или ошибку.
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(Principal principal) {
        // Проверяем, аутентифицирован ли пользователь
        if (principal == null) {
            log.warn("Попытка logout без аутентификации.");
            // Возвращаем 401 Unauthorized, если запросу не удалось пройти аутентификацию
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        log.info("POST /api/auth/logout - Запрос на выход для пользователя: {}", principal.getName());

        // Получаем текущий объект Authentication из SecurityContext
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        // Извлекаем userId из токена (самый надежный способ)
        // Предполагаем, что в credentials хранится сам токен (как установлено в JwtAuthenticationFilter)
        if (authentication != null && authentication.isAuthenticated() && authentication.getCredentials() instanceof String) {
            String token = (String) authentication.getCredentials();
            UUID userId = jwtTokenProvider.getUserIdFromToken(token); // Извлекаем ID пользователя

            if (userId != null) {
                // Вызываем метод logout в сервисе для инвалидации refresh токена
                authService.logout(userId);
                log.info("Пользователь {} (ID: {}) успешно вышел из системы.", principal.getName(), userId);
                // Возвращаем статус 200 OK
                return ResponseEntity.ok().build();
            } else {
                // Ситуация, когда токен валиден, но не содержит userId (маловероятно при нашей логике)
                log.error("Не удалось извлечь userId из токена для пользователя {} при logout.", principal.getName());
                // Возвращаем ошибку сервера
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        } else {
            // Не удалось получить аутентификацию или токен из контекста
            log.warn("Не удалось получить токен из SecurityContext для пользователя {} при logout.", principal.getName());
            // Возвращаем 401, т.к. не можем идентифицировать пользователя для logout
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }


    // --- Эндпоинты для Telegram ---

    /**
     * Обрабатывает запрос на первичную аутентификацию через Telegram WebApp initData.
     * @param request DTO с initData.
     * @return ResponseEntity с TelegramAuthResult (либо токены, либо данные для входа/регистрации).
     */
    @PostMapping("/telegram/authenticate")
    public ResponseEntity<TelegramAuthResult> telegramAuth(@Valid @RequestBody TelegramAuthRequest request) {
        log.info("POST /api/auth/telegram/authenticate - Аутентификация через Telegram initData.");
        // Делегируем логику в TelegramAuthService
        TelegramAuthResult result = telegramAuthService.authenticateTelegramUser(request);
        return ResponseEntity.ok(result);
    }

    /**
     * Обрабатывает запрос на вход пользователя по логину/паролю с привязкой Telegram ID.
     * @param request DTO с логином, паролем и Telegram ID.
     * @return ResponseEntity с JwtResponse при успехе.
     */
    @PostMapping("/telegram/login")
    public ResponseEntity<JwtResponse> telegramLogin(@Valid @RequestBody TelegramLoginRequest request) {
        log.info("POST /api/auth/telegram/login - Вход пользователя {} с привязкой Telegram ID {}.", request.getUsername(), request.getTelegramId());
        // Делегируем логику в TelegramAuthService
        JwtResponse jwtResponse = telegramAuthService.loginWithTelegram(request);
        return ResponseEntity.ok(jwtResponse);
    }

    /**
     * Обрабатывает запрос на регистрацию нового пользователя через Telegram с вводом пароля.
     * @param request DTO с данными регистрации и Telegram ID.
     * @return ResponseEntity с JwtResponse при успехе.
     */
    @PostMapping("/telegram/register")
    public ResponseEntity<JwtResponse> telegramRegister(@Valid @RequestBody TelegramRegisterRequest request) {
        log.info("POST /api/auth/telegram/register - Регистрация пользователя {} с Telegram ID {}.", request.getUsername(), request.getTelegramId());
        // Делегируем логику в TelegramAuthService
        JwtResponse jwtResponse = telegramAuthService.registerWithTelegram(request);
        // Возвращаем токены и статус 201 Created
        return ResponseEntity.status(HttpStatus.CREATED).body(jwtResponse);
    }

    // --- Дополнительные эндпоинты (Пример) ---

    /**
     * Эндпоинт для проверки валидности текущего access токена.
     * Просто требует аутентификации. Если запрос дошел сюда, токен валиден (проверен фильтром).
     * @param principal Объект Principal аутентифицированного пользователя.
     * @return ResponseEntity<Void> со статусом 200 OK.
     */
    @GetMapping("/me/validate")
    public ResponseEntity<Void> validateToken(Principal principal) {
        log.debug("GET /api/auth/me/validate - Проверка валидности токена для пользователя: {}", principal != null ? principal.getName() : "anonymous");
        // Логика проверки уже выполнена фильтром JwtAuthenticationFilter.
        // Если запрос дошел сюда, значит пользователь аутентифицирован.
        return ResponseEntity.ok().build();
    }
}
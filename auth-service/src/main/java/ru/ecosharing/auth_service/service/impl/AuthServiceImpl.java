package ru.ecosharing.auth_service.service.impl;

import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.ecosharing.auth_service.client.NotificationClient;
import ru.ecosharing.auth_service.client.UserServiceClient;
import ru.ecosharing.auth_service.config.AppProperties;
import ru.ecosharing.auth_service.dto.enumeration.NotificationType;
import ru.ecosharing.auth_service.dto.request.*;
import ru.ecosharing.auth_service.dto.response.AvailabilityCheckResponse;
import ru.ecosharing.auth_service.dto.response.JwtResponse;
import ru.ecosharing.auth_service.dto.response.UserCredentialsResponse;
import ru.ecosharing.auth_service.model.*;
import ru.ecosharing.auth_service.exception.*; // Импорт кастомных исключений
import ru.ecosharing.auth_service.repository.*;
import ru.ecosharing.auth_service.security.JwtTokenProvider;
import ru.ecosharing.auth_service.service.AuthService;
import ru.ecosharing.auth_service.service.RefreshTokenService;


import java.util.*;
import java.util.stream.Collectors;

/**
 * Реализация сервиса стандартной аутентификации и регистрации.
 */
@Slf4j
@Service
@RequiredArgsConstructor // Внедрение зависимостей через конструктор
public class AuthServiceImpl implements AuthService {

    // --- Зависимости ---
    private final AuthenticationManager authenticationManager; // Менеджер аутентификации Spring Security
    private final UserCredentialsRepository userCredentialsRepository; // Репозиторий учетных данных
    private final RoleRepository roleRepository; // Репозиторий ролей
    private final PasswordEncoder passwordEncoder; // Кодировщик паролей
    private final JwtTokenProvider jwtTokenProvider; // Провайдер JWT токенов
    private final RefreshTokenService refreshTokenService; // Сервис для refresh токенов
    private final UserServiceClient userServiceClient; // Клиент для взаимодействия с User Service
    private final NotificationClient notificationClient; // Клиент для отправки уведомлений
    private final AppProperties appProperties; // Доступ к свойствам приложения (секреты, время жизни токенов)

    /**
     * Аутентификация пользователя по логину и паролю.
     */
    @Override
    @Transactional // Оборачиваем в транзакцию (хотя здесь в основном чтение, но создание refresh токена - запись)
    public JwtResponse login(LoginRequest loginRequest) {
        log.info("Попытка входа для логина: {}", loginRequest.getLogin());

        // 1. Используем AuthenticationManager для проверки логина/пароля.
        // Он вызовет UserDetailsServiceImpl, который обратится к User Service и локальной БД.
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            loginRequest.getLogin(), // Логин (username или email)
                            loginRequest.getPassword() // Пароль
                    )
            );
        } catch (BadCredentialsException e) {
            // Неверный пароль или логин (если UserDetailsServiceImpl бросил UsernameNotFoundException)
            log.warn("Неудачная попытка входа для логина {}: Неверные учетные данные", loginRequest.getLogin());
            throw new InvalidCredentialsException("Неверный логин или пароль.");
        } catch (UsernameNotFoundException e) { // Хотя UserDetailsServiceImpl бросает это, оно может быть обернуто
            log.warn("Неудачная попытка входа для логина {}: {}", loginRequest.getLogin(), e.getMessage());
            throw new InvalidCredentialsException("Неверный логин или пароль."); // Не раскрываем детали
        } catch (Exception e) {
            // Другие возможные ошибки (например, UserDeactivatedException из UserDetailsServiceImpl)
            log.error("Ошибка во время аутентификации для логина {}: {}", loginRequest.getLogin(), e.getMessage(), e);
            // Проверяем причину исключения, чтобы вернуть специфичную ошибку, если возможно
            Throwable cause = e.getCause();
            if (cause instanceof UserDeactivatedException) {
                throw (UserDeactivatedException) cause;
            }
            if (cause instanceof AuthenticationProcessException) {
                throw (AuthenticationProcessException) cause;
            }
            throw new AuthenticationProcessException("Произошла ошибка во время входа.", e);
        }

        // 2. Устанавливаем успешную аутентификацию в SecurityContext
        SecurityContextHolder.getContext().setAuthentication(authentication);

        // 3. Получаем детали пользователя из объекта Authentication
        org.springframework.security.core.userdetails.User userDetails =
                (org.springframework.security.core.userdetails.User) authentication.getPrincipal();
        String username = userDetails.getUsername(); // Имя пользователя (из User Service)

        // 4. Получаем userId (он должен был быть получен в UserDetailsServiceImpl, но безопасно запросить снова)
        UUID userId = findUserIdByLogin(username); // Используем вспомогательный метод

        // 5. Генерируем Access и Refresh токены
        String accessToken = jwtTokenProvider.createAccessToken(authentication, userId);
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(userId, username); // Создаем/пересоздаем refresh токен

        log.info("Пользователь {} (ID: {}) успешно вошел в систему.", username, userId);

        // 6. Формируем и возвращаем ответ
        return buildJwtResponse(accessToken, refreshToken, userId, authentication);
    }

    /**
     * Регистрация нового пользователя.
     */
    @Override
    @Transactional // Критически важно использовать транзакцию!
    public JwtResponse register(RegisterRequest registerRequest) {
        log.info("Попытка регистрации нового пользователя: {}", registerRequest.getUsername());

        // 1. Проверка доступности username и email через User Service
        checkUserAvailability(registerRequest.getUsername(), registerRequest.getEmail());

        // 2. Генерируем новый уникальный ID для пользователя
        UUID newUserId = UUID.randomUUID();

        // 3. Определяем роли пользователя (с проверкой секретных паролей)
        Set<Role> roles = determineUserRoles(registerRequest.getPassword());

        // 4. Создаем и сохраняем учетные данные в Auth Service
        UserCredentials credentials = UserCredentials.builder()
                .userId(newUserId)
                .passwordHash(passwordEncoder.encode(registerRequest.getPassword())) // Хешируем ПАРОЛЬ ПОЛЬЗОВАТЕЛЯ
                .isActive(true)
                .roles(roles)
                // telegramId здесь null, т.к. это стандартная регистрация
                .build();
        userCredentialsRepository.save(credentials);
        log.info("Учетные данные для пользователя {} (ID: {}) сохранены в Auth Service.",
                registerRequest.getUsername(), newUserId);

        // 5. Вызываем User Service для создания профиля пользователя
        createProfileInUserService(newUserId, registerRequest);

        // 6. Отправка уведомления о регистрации (опционально)
        sendRegistrationNotification(registerRequest.getFirstName(), registerRequest.getUsername());

        // 7. Генерируем токены для нового пользователя
        Authentication authentication = createAuthentication(registerRequest.getUsername(), roles);
        String accessToken = jwtTokenProvider.createAccessToken(authentication, newUserId);
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(newUserId, registerRequest.getUsername());

        log.info("Пользователь {} (ID: {}) успешно зарегистрирован.", registerRequest.getUsername(), newUserId);

        // 8. Возвращаем ответ с токенами
        return buildJwtResponse(accessToken, refreshToken, newUserId, authentication);
    }

    /**
     * Обновление токенов с использованием Refresh токена.
     */
    @Override
    @Transactional(noRollbackFor = TokenRefreshException.class) // Не откатываем транзакцию, если токен просто истек или не найден
    public JwtResponse refreshToken(RefreshTokenRequest refreshTokenRequest) {
        String requestRefreshToken = refreshTokenRequest.getRefreshToken();
        log.debug("Попытка обновления токена с использованием refresh токена: {}...", requestRefreshToken.substring(0, Math.min(requestRefreshToken.length(), 10)));

        // 1. Находим и проверяем refresh токен
        RefreshToken refreshToken = refreshTokenService.findByToken(requestRefreshToken)
                .map(token -> {
                    try {
                        // Проверяем срок годности (удаляет, если истек и бросает исключение)
                        return refreshTokenService.verifyExpiration(token);
                    } catch (TokenRefreshException e) {
                        log.warn("Refresh токен истек: {}", e.getMessage());
                        throw e; // Пробрасываем исключение дальше
                    }
                })
                .orElseThrow(() -> {
                    log.warn("Refresh токен не найден в базе данных.");
                    return new TokenRefreshException(requestRefreshToken, "Refresh токен не найден.");
                });

        // 2. Получаем учетные данные пользователя
        UserCredentials userCredentials = refreshToken.getUserCredentials();

        // 3. Проверяем локальный статус активности пользователя
        if (!userCredentials.isActive()) {
            log.warn("Попытка обновить токен для неактивного пользователя ID: {}", userCredentials.getUserId());
            refreshTokenService.deleteToken(requestRefreshToken); // Удаляем невалидный токен
            throw new UserDeactivatedException("Учетная запись пользователя деактивирована.");
        }

        // 4. Получаем актуальный username и статус из User Service
        UserCredentialsResponse userProfile = getUserProfileFromUserService(userCredentials.getUserId(), requestRefreshToken);
        if (!userProfile.isActive()) {
            log.warn("Пользователь ID: {} деактивирован в User Service. Обновление токена отклонено.", userCredentials.getUserId());
            refreshTokenService.deleteToken(requestRefreshToken); // Удаляем старый токен
            throw new UserDeactivatedException("Учетная запись пользователя деактивирована.");
        }
        String username = userProfile.getUsername();

        // 5. Создаем новый Access Token
        Authentication authentication = createAuthentication(username, userCredentials.getRoles());
        String newAccessToken = jwtTokenProvider.createAccessToken(authentication, userCredentials.getUserId());

        // 6. Создаем новый Refresh Token (рекомендуется перевыпускать)
        // Старый токен был удален в методе createRefreshToken сервиса RefreshTokenService
        RefreshToken newRefreshToken = refreshTokenService.createRefreshToken(userCredentials.getUserId(), username);

        log.info("Токены успешно обновлены для пользователя {} (ID: {})", username, userCredentials.getUserId());

        // 7. Возвращаем ответ с новыми токенами
        return buildJwtResponse(newAccessToken, newRefreshToken, userCredentials.getUserId(), authentication);
    }

    /**
     * Выход пользователя из системы (удаление refresh токенов).
     */
    @Override
    @Transactional
    public void logout(UUID userId) {
        log.info("Выход из системы для пользователя ID: {}", userId);
        refreshTokenService.deleteByUserId(userId); // Удаляем все refresh токены пользователя
        Optional<UserCredentials> credentialsOpt = userCredentialsRepository.findByUserId(userId);
        if (credentialsOpt.isPresent()) {
            UserCredentials credentials = credentialsOpt.get();
            if (credentials.getTelegramId() != null) {
                log.debug("Отвязка Telegram ID {} от пользователя ID {}", credentials.getTelegramId(), userId);
                credentials.setTelegramId(null); // Устанавливаем поле в null
                try {
                    userCredentialsRepository.save(credentials); // Сохраняем изменения в БД
                    log.info("Telegram ID успешно отвязан для пользователя ID {}", userId);
                } catch (Exception e) {
                    // Логируем ошибку сохранения, но не прерываем процесс logout
                    log.error("Ошибка при сохранении изменений (отвязке Telegram ID) для пользователя ID {}: {}", userId, e.getMessage(), e);
                    // В реальном приложении можно добавить механизм повторных попыток или уведомление администратора
                }
            } else {
                // Если Telegram ID уже был null, ничего не делаем
                log.debug("У пользователя ID {} не был привязан Telegram ID.", userId);
            }
        } else {
            // Если по какой-то причине учетные данные не найдены (маловероятно для аутентифицированного пользователя)
            log.warn("Не найдены учетные данные для пользователя ID {} при попытке отвязки Telegram ID во время logout.", userId);
        }

        // 3. Очищаем SecurityContext (как и раньше)
        SecurityContextHolder.clearContext();
    }

    // =========================================================================
    // --- Вспомогательные приватные методы ---
    // =========================================================================

    /**
     * Находит UserId по логину (username или email), обращаясь к User Service.
     * Выбрасывает UsernameNotFoundException или AuthenticationProcessException.
     */
    private UUID findUserIdByLogin(String login) {
        try {
            ResponseEntity<UserCredentialsResponse> response = userServiceClient.findUserByLogin(login);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                // Если User Service вернул что-то кроме 2xx или пустое тело
                log.warn("User Service вернул не 2xx или пустое тело для логина {}", login);
                throw new UsernameNotFoundException("Пользователь не найден с логином: " + login);
            }
            return response.getBody().getUserId();
        } catch (FeignException.NotFound e) {
            // User Service явно вернул 404
            log.warn("User Service не нашел пользователя по логину: {}", login);
            throw new UsernameNotFoundException("Пользователь не найден с логином: " + login);
        } catch (FeignException e) {
            // Другие ошибки Feign
            log.error("Ошибка Feign при поиске userId по логину {} в User Service: статус={}, тело={}", login, e.status(), e.contentUTF8(), e);
            throw new AuthenticationProcessException("Ошибка связи с сервисом пользователей при поиске пользователя.", e);
        } catch (Exception e) {
            // Непредвиденные ошибки
            log.error("Неожиданная ошибка при поиске userId по логину {} в User Service:", login, e);
            throw new AuthenticationProcessException("Внутренняя ошибка при поиске пользователя.", e);
        }
    }

    /**
     * Проверяет доступность username и email через User Service.
     * Выбрасывает исключения UsernameAlreadyExistsException или EmailAlreadyExistsException, если заняты,
     * или RegistrationException при ошибках связи.
     */
    private void checkUserAvailability(String username, String email) {
        try {
            AvailabilityCheckRequest checkRequest = new AvailabilityCheckRequest(username, email);
            ResponseEntity<AvailabilityCheckResponse> checkResponse = userServiceClient.checkAvailability(checkRequest);

            if (!checkResponse.getStatusCode().is2xxSuccessful() || checkResponse.getBody() == null) {
                log.error("Ошибка при проверке доступности в User Service для username: {}, email: {}. Ответ: {}",
                        username, email, checkResponse.getStatusCode());
                throw new RegistrationException("Не удалось проверить доступность имени пользователя или email.");
            }

            AvailabilityCheckResponse availability = checkResponse.getBody();
            if (!availability.isUsernameAvailable()) {
                log.warn("Попытка регистрации с уже занятым username: {}", username);
                throw new UsernameAlreadyExistsException("Имя пользователя '" + username + "' уже занято.");
            }
            if (!availability.isEmailAvailable()) {
                log.warn("Попытка регистрации с уже занятым email: {}", email);
                throw new EmailAlreadyExistsException("Email '" + email + "' уже используется.");
            }
            log.debug("Username '{}' и Email '{}' доступны.", username, email);
        } catch (FeignException e) {
            log.error("Ошибка Feign при проверке доступности для username={}, email={}: статус={}, тело={}",
                    username, email, e.status(), e.contentUTF8(), e);
            throw new RegistrationException("Ошибка связи с сервисом пользователей при проверке доступности.", e);
        } catch (Exception e) {
            log.error("Неожиданная ошибка при проверке доступности для username={}, email={}:", username, email, e);
            throw new RegistrationException("Внутренняя ошибка при проверке доступности.", e);
        }
    }

    /**
     * Определяет набор ролей для нового пользователя на основе введенного пароля.
     */
    private Set<Role> determineUserRoles(String rawPassword) {
        Set<Role> roles = new HashSet<>();
        // Базовая роль пользователя
        roles.add(findRoleOrThrow(RoleName.ROLE_USER));

        // Проверка секретных паролей
        if (rawPassword.equals(appProperties.getAdminSecretPassword())) {
            roles.add(findRoleOrThrow(RoleName.ROLE_ADMIN));
            log.info("Обнаружен пароль администратора при регистрации.");
        } else if (rawPassword.equals(appProperties.getModeratorSecretPassword())) {
            roles.add(findRoleOrThrow(RoleName.ROLE_MODERATOR));
            log.info("Обнаружен пароль модератора при регистрации.");
        }
        return roles;
    }

    /**
     * Находит роль по имени или выбрасывает исключение RegistrationException.
     */
    private Role findRoleOrThrow(RoleName roleName) {
        return roleRepository.findByName(roleName)
                .orElseThrow(() -> {
                    log.error("Критическая ошибка: Роль {} не найдена в базе данных!", roleName);
                    // Это критическая ошибка конфигурации, регистрация невозможна
                    return new RegistrationException("Ошибка конфигурации: роль " + roleName + " не найдена.");
                });
    }

    /**
     * Вызывает User Service для создания профиля.
     * Выбрасывает RegistrationException при ошибке, что приводит к откату транзакции.
     */
    private void createProfileInUserService(UUID userId, RegisterRequest request) {
        CreateUserProfileRequest profileRequest = CreateUserProfileRequest.builder()
                .userId(userId)
                .username(request.getUsername())
                .email(request.getEmail())
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .phone(request.getPhone())
                .build();
        try {
            ResponseEntity<Void> profileResponse = userServiceClient.createUserProfile(profileRequest);
            // Проверяем статус ответа от User Service
            if (!profileResponse.getStatusCode().is2xxSuccessful()) { // Обычно ожидаем 201 Created
                log.error("User Service не смог создать профиль для пользователя {} (ID: {}). Статус: {}. Откат транзакции.",
                        request.getUsername(), userId, profileResponse.getStatusCode());
                // Выбрасываем исключение, чтобы откатить сохранение учетных данных в Auth Service
                throw new RegistrationException("Не удалось создать профиль пользователя. Регистрация отменена.");
            }
            log.info("Профиль для пользователя {} (ID: {}) успешно создан в User Service.", request.getUsername(), userId);
        } catch (FeignException e) {
            log.error("Ошибка Feign при создании профиля в User Service для пользователя {} (ID: {}): статус={}, тело={}. Откат.",
                    request.getUsername(), userId, e.status(), e.contentUTF8(), e);
            throw new RegistrationException("Ошибка связи с сервисом пользователей при создании профиля. Регистрация отменена.", e);
        } catch (Exception e) {
            log.error("Неожиданная ошибка при создании профиля в User Service для пользователя {} (ID: {}): {}. Откат.",
                    request.getUsername(), userId, e.getMessage(), e);
            throw new RegistrationException("Внутренняя ошибка при создании профиля. Регистрация отменена.", e);
        }
    }

    /**
     * Отправляет уведомление о регистрации (если настроен NotificationClient).
     * Ошибки отправки не влияют на процесс регистрации.
     */
    private void sendRegistrationNotification(String firstName, String username) {
        try {
            // Собираем параметры для шаблона уведомления
            Map<String, String> params = new HashMap<>();
            params.put("firstName", firstName != null ? firstName : "Пользователь"); // Имя по умолчанию
            params.put("username", username);

            NotificationRequest notificationRequest = new NotificationRequest();
            // В данном случае chatId неизвестен, уведомление общее или не отправляется
            // notificationRequest.setChatId(...);
            notificationRequest.setNotificationType(NotificationType.REGISTRATION_COMPLETE);
            notificationRequest.setParams(params);

            // Попытка отправки (раскомментировать, если есть получатель)
            // notificationClient.sendNotification(notificationRequest);
            log.debug("Уведомление о регистрации подготовлено (не отправлено без Chat ID).");
        } catch (Exception e) {
            // Не критично, просто логируем ошибку
            log.error("Ошибка при подготовке/отправке уведомления о регистрации для {}: {}", username, e.getMessage(), e);
        }
    }

    /**
     * Создает объект Authentication на основе username и ролей.
     * Используется для генерации токена после регистрации.
     */
    private Authentication createAuthentication(String username, Set<Role> roles) {
        List<GrantedAuthority> authorities = roles.stream()
                .map(role -> new SimpleGrantedAuthority(role.getName().getRole()))
                .collect(Collectors.toList());
        // Пароль не передается, т.к. аутентификация уже как бы прошла (регистрация успешна)
        return new UsernamePasswordAuthenticationToken(username, null, authorities);
    }

    /**
     * Получает профиль пользователя из User Service по ID.
     * Используется при обновлении токена. Выбрасывает TokenRefreshException при ошибках.
     */
    private UserCredentialsResponse getUserProfileFromUserService(UUID userId, String requestRefreshToken) {
        try {
            ResponseEntity<UserCredentialsResponse> response = userServiceClient.findUserById(userId);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.error("Не удалось получить данные из User Service для userId: {} при обновлении токена. Статус: {}",
                        userId, response.getStatusCode());
                // Считаем это ошибкой процесса обновления токена
                throw new TokenRefreshException(requestRefreshToken, "Ошибка получения данных пользователя для обновления токена.");
            }
            return response.getBody();
        } catch (FeignException e) {
            log.error("Ошибка Feign при запросе данных пользователя для userId: {} из User Service при обновлении токена: статус={}, тело={}",
                    userId, e.status(), e.contentUTF8(), e);
            throw new TokenRefreshException(requestRefreshToken, "Ошибка связи с сервисом пользователей при обновлении токена.", e);
        } catch (Exception e) {
            log.error("Неожиданная ошибка при запросе данных пользователя для userId: {} из User Service при обновлении токена:", userId, e);
            throw new TokenRefreshException(requestRefreshToken, "Внутренняя ошибка при обновлении токена.", e);
        }
    }

    /**
     * Собирает финальный JwtResponse DTO из полученных данных.
     */
    private JwtResponse buildJwtResponse(String accessToken, RefreshToken refreshToken, UUID userId, Authentication authentication) {
        return JwtResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken.getToken()) // Используем строку токена из объекта RefreshToken
                .userId(userId)
                .roles(authentication.getAuthorities().stream() // Получаем роли из объекта Authentication
                        .map(GrantedAuthority::getAuthority)
                        .collect(Collectors.toList()))
                .accessTokenExpiresInMs(appProperties.getJwtAccessExpirationMs()) // Время жизни из настроек
                .refreshTokenExpiresInMs(appProperties.getJwtRefreshExpirationMs()) // Время жизни из настроек
                .build();
    }
}
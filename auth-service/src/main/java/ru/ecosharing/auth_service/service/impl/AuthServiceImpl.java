package ru.ecosharing.auth_service.service.impl;

import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity; // Важно для обработки ответа Feign
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
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
import ru.ecosharing.auth_service.client.UserServiceClient;
import ru.ecosharing.auth_service.config.AppProperties;
import ru.ecosharing.auth_service.dto.NotificationRequestKafkaDto; // DTO для Kafka
import ru.ecosharing.auth_service.dto.enums.NotificationType;
import ru.ecosharing.auth_service.dto.request.*;
import ru.ecosharing.auth_service.dto.response.AvailabilityCheckResponse; // DTO ответа Feign
import ru.ecosharing.auth_service.dto.response.JwtResponse;
import ru.ecosharing.auth_service.dto.response.UserCredentialsResponse; // DTO ответа Feign
import ru.ecosharing.auth_service.exception.*;
import ru.ecosharing.auth_service.model.*; // Включая NotificationType
import ru.ecosharing.auth_service.repository.*;
import ru.ecosharing.auth_service.security.JwtTokenProvider;
import ru.ecosharing.auth_service.service.AuthService;
import ru.ecosharing.auth_service.service.RefreshTokenService;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Реализация сервиса стандартной аутентификации и регистрации.
 * Использует Feign для синхронного взаимодействия с User Service при создании профиля
 * и Kafka для асинхронной отправки запроса на уведомление.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserCredentialsRepository userCredentialsRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;
    private final UserServiceClient userServiceClient; // Твой Feign клиент
    private final AppProperties appProperties;
    private final KafkaTemplate<String, NotificationRequestKafkaDto> notificationKafkaTemplate;

    @Value("${kafka.topic.notification-requests}")
    private String notificationRequestTopic;

    @Override
    @Transactional
    public JwtResponse login(LoginRequest loginRequest) {
        log.info("Попытка входа для логина: {}", loginRequest.getLogin());
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(loginRequest.getLogin(), loginRequest.getPassword())
            );
        } catch (BadCredentialsException e) {
            log.warn("Неудачная попытка входа для логина {}: Неверные учетные данные", loginRequest.getLogin());
            throw new InvalidCredentialsException("Неверный логин или пароль.");
        } catch (UsernameNotFoundException e) {
            log.warn("Неудачная попытка входа для логина {}: {}", loginRequest.getLogin(), e.getMessage());
            throw new InvalidCredentialsException("Неверный логин или пароль.");
        } catch (Exception e) {
            Throwable cause = e.getCause();
            if (cause instanceof UserDeactivatedException) throw (UserDeactivatedException) cause;
            if (cause instanceof AuthenticationProcessException) throw (AuthenticationProcessException) cause;
            log.error("Ошибка во время аутентификации для логина {}: {}", loginRequest.getLogin(), e.getMessage(), e);
            throw new AuthenticationProcessException("Произошла ошибка во время входа.", e);
        }
        SecurityContextHolder.getContext().setAuthentication(authentication);
        org.springframework.security.core.userdetails.User userDetails = (org.springframework.security.core.userdetails.User) authentication.getPrincipal();
        String username = userDetails.getUsername();
        UUID userId = findUserIdByLogin(username); // Используем вспомогательный метод
        String accessToken = jwtTokenProvider.createAccessToken(authentication, userId);
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(userId, username);
        log.info("Пользователь {} (ID: {}) успешно вошел в систему.", username, userId);
        return buildJwtResponse(accessToken, refreshToken, userId, authentication);
    }

    @Override
    @Transactional
    public JwtResponse register(RegisterRequest registerRequest) {
        log.info("Попытка регистрации нового пользователя: {}", registerRequest.getUsername());

        // 1. Проверка доступности username и email через User Service (используя Feign)
        checkUserAvailability(registerRequest.getUsername(), registerRequest.getEmail());

        // 2. Генерируем новый userId
        UUID newUserId = UUID.randomUUID();

        // 3. Определяем роли
        Set<Role> roles = determineUserRoles(registerRequest.getPassword());

        // 4. Создаем и сохраняем учетные данные
        UserCredentials credentials = UserCredentials.builder()
                .userId(newUserId)
                .passwordHash(passwordEncoder.encode(registerRequest.getPassword()))
                .isActive(true)
                .roles(roles)
                .telegramId(null) // Обычная регистрация
                .build();
        userCredentialsRepository.save(credentials);
        log.info("Учетные данные для пользователя {} (ID: {}) сохранены.", registerRequest.getUsername(), newUserId);

        // 5. Создаем профиль в User Service (синхронный Feign вызов)
        createProfileInUserService(newUserId, registerRequest);

        // 6. Отправка уведомления о регистрации через Kafka
        sendRegistrationNotificationViaKafka(newUserId, registerRequest.getFirstName(), registerRequest.getUsername());

        // 7. Генерируем токены
        Authentication authentication = createAuthentication(registerRequest.getUsername(), roles);
        String accessToken = jwtTokenProvider.createAccessToken(authentication, newUserId);
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(newUserId, registerRequest.getUsername());

        log.info("Пользователь {} (ID: {}) успешно зарегистрирован.", registerRequest.getUsername(), newUserId);
        return buildJwtResponse(accessToken, refreshToken, newUserId, authentication);
    }

    @Override
    @Transactional(noRollbackFor = TokenRefreshException.class)
    public JwtResponse refreshToken(RefreshTokenRequest refreshTokenRequest) {
        String requestRefreshTokenStr = refreshTokenRequest.getRefreshToken();
        log.debug("Попытка обновления токена: {}...", requestRefreshTokenStr.substring(0, Math.min(requestRefreshTokenStr.length(), 10)));

        RefreshToken refreshToken = refreshTokenService.findByToken(requestRefreshTokenStr)
                .map(token -> {
                    try { return refreshTokenService.verifyExpiration(token); }
                    catch (TokenRefreshException e) { log.warn("Refresh токен истек: {}", e.getMessage()); throw e; }
                })
                .orElseThrow(() -> new TokenRefreshException(requestRefreshTokenStr, "Refresh токен не найден."));

        UserCredentials userCredentials = refreshToken.getUserCredentials();
        if (!userCredentials.isActive()) {
            log.warn("Попытка обновить токен для неактивного пользователя ID: {}", userCredentials.getUserId());
            refreshTokenService.deleteToken(requestRefreshTokenStr);
            throw new UserDeactivatedException("Учетная запись пользователя деактивирована.");
        }

        UserCredentialsResponse userProfile = getUserProfileFromUserService(userCredentials.getUserId(), requestRefreshTokenStr);
        if (!userProfile.isActive()) {
            log.warn("Пользователь ID: {} деактивирован в User Service. Обновление токена отклонено.", userCredentials.getUserId());
            refreshTokenService.deleteToken(requestRefreshTokenStr);
            throw new UserDeactivatedException("Учетная запись пользователя деактивирована.");
        }
        String username = userProfile.getUsername();

        Authentication authentication = createAuthentication(username, userCredentials.getRoles());
        String newAccessToken = jwtTokenProvider.createAccessToken(authentication, userCredentials.getUserId());
        RefreshToken newRefreshToken = refreshTokenService.createRefreshToken(userCredentials.getUserId(), username);

        log.info("Токены успешно обновлены для пользователя {} (ID: {})", username, userCredentials.getUserId());
        return buildJwtResponse(newAccessToken, newRefreshToken, userCredentials.getUserId(), authentication);
    }

    @Override
    @Transactional
    public void logout(UUID userId) {
        log.info("Выход из системы для пользователя ID: {}", userId);
        refreshTokenService.deleteByUserId(userId);
        userCredentialsRepository.findByUserId(userId).ifPresent(credentials -> {
            if (credentials.getTelegramId() != null) {
                log.debug("Отвязка Telegram ID {} от пользователя ID {}", credentials.getTelegramId(), userId);
                credentials.setTelegramId(null);
                try { userCredentialsRepository.save(credentials); log.info("Telegram ID успешно отвязан для пользователя ID {}", userId); }
                catch (Exception e) { log.error("Ошибка при сохранении отвязки Telegram ID для пользователя ID {}: {}", userId, e.getMessage(), e); }
            }
        });
        SecurityContextHolder.clearContext();
    }

    // --- Вспомогательные приватные методы ---

    private UUID findUserIdByLogin(String login) {
        try {
            // Используем Feign клиент, который возвращает ResponseEntity
            ResponseEntity<UserCredentialsResponse> responseEntity = userServiceClient.findUserByLogin(login);

            if (!responseEntity.getStatusCode().is2xxSuccessful() || responseEntity.getBody() == null) {
                log.warn("User Service вернул ошибку или пустое тело для логина {}. Статус: {}", login, responseEntity.getStatusCode());
                throw new UsernameNotFoundException("Пользователь не найден с логином или email: " + login);
            }
            UserCredentialsResponse userCredentialsDto = responseEntity.getBody();
            return userCredentialsDto.getUserId();
        } catch (FeignException.NotFound e) { // FeignException.NotFound - если User Service вернул 404
            log.warn("User Service не нашел пользователя по логину: {}", login);
            throw new UsernameNotFoundException("Пользователь не найден с логином или email: " + login);
        } catch (FeignException e) { // Другие ошибки Feign
            log.error("Ошибка Feign при поиске userId по логину {} в User Service: статус {}, тело {}",
                    login, e.status(), e.contentUTF8(), e);
            throw new AuthenticationProcessException("Ошибка связи с сервисом пользователей при поиске пользователя.", e);
        } catch (Exception e) { // Другие неожиданные ошибки
            log.error("Неожиданная ошибка при поиске userId по логину {} в User Service:", login, e);
            throw new AuthenticationProcessException("Внутренняя ошибка при поиске пользователя.", e);
        }
    }

    private void checkUserAvailability(String username, String email) {
        try {
            AvailabilityCheckRequest checkRequest = new AvailabilityCheckRequest(username, email);
            ResponseEntity<AvailabilityCheckResponse> responseEntity = userServiceClient.checkAvailability(checkRequest);

            if (!responseEntity.getStatusCode().is2xxSuccessful() || responseEntity.getBody() == null) {
                log.error("Ошибка при проверке доступности в User Service для username: {}, email: {}. Статус: {}",
                        username, email, responseEntity.getStatusCode());
                throw new RegistrationException("Не удалось проверить доступность имени пользователя или email.");
            }
            AvailabilityCheckResponse availability = responseEntity.getBody();
            if (!availability.isUsernameAvailable()) {
                throw new UsernameAlreadyExistsException("Имя пользователя '" + username + "' уже занято.");
            }
            if (!availability.isEmailAvailable()) {
                throw new EmailAlreadyExistsException("Email '" + email + "' уже используется.");
            }
            log.debug("Username '{}' и Email '{}' доступны.", username, email);
        } catch (FeignException e) {
            log.error("Ошибка Feign при проверке доступности для username={}, email={}: статус {}, тело {}",
                    username, email, e.status(), e.contentUTF8(), e);
            throw new RegistrationException("Ошибка связи с сервисом пользователей при проверке доступности.", e);
        } catch (Exception e) {
            log.error("Неожиданная ошибка при проверке доступности для username={}, email={}:", username, email, e);
            throw new RegistrationException("Внутренняя ошибка при проверке доступности.", e);
        }
    }

    private Set<Role> determineUserRoles(String rawPassword) {
        Set<Role> roles = new HashSet<>();
        roles.add(findRoleOrThrow(RoleName.ROLE_USER));
        if (rawPassword.equals(appProperties.getAdminSecretPassword())) {
            roles.add(findRoleOrThrow(RoleName.ROLE_ADMIN));
            log.info("Назначена роль ADMIN по секретному паролю.");
        } else if (rawPassword.equals(appProperties.getModeratorSecretPassword())) {
            roles.add(findRoleOrThrow(RoleName.ROLE_MODERATOR));
            log.info("Назначена роль MODERATOR по секретному паролю.");
        }
        return roles;
    }

    private Role findRoleOrThrow(RoleName roleName) {
        return roleRepository.findByName(roleName)
                .orElseThrow(() -> new RegistrationException("Ошибка конфигурации: роль " + roleName + " не найдена."));
    }

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
            // Синхронный вызов User Service для создания профиля
            ResponseEntity<Void> profileResponseEntity = userServiceClient.createUserProfile(profileRequest);
            if (!profileResponseEntity.getStatusCode().is2xxSuccessful()) { // Ожидаем 201 Created
                log.error("User Service не смог создать профиль для пользователя {} (ID: {}). Статус: {}. Откат.",
                        request.getUsername(), userId, profileResponseEntity.getStatusCode());
                throw new RegistrationException("Не удалось создать профиль пользователя. Регистрация отменена.");
            }
            log.info("Профиль для пользователя {} (ID: {}) успешно создан в User Service.", request.getUsername(), userId);
        } catch (FeignException e) {
            log.error("Ошибка Feign при создании профиля в User Service для пользователя {} (ID: {}): статус {}, тело {}. Откат.",
                    request.getUsername(), userId, e.status(), e.contentUTF8(), e);
            throw new RegistrationException("Ошибка связи с сервисом пользователей при создании профиля. Регистрация отменена.", e);
        } catch (Exception e) {
            log.error("Неожиданная ошибка при создании профиля в User Service для пользователя {} (ID: {}): {}. Откат.",
                    request.getUsername(), userId, e.getMessage(), e);
            throw new RegistrationException("Внутренняя ошибка при создании профиля. Регистрация отменена.", e);
        }
    }

    private void sendRegistrationNotificationViaKafka(UUID userId, String firstName, String username) {
        try {
            Map<String, String> params = new HashMap<>();
            params.put("firstName", firstName != null ? firstName : username);
            params.put("username", username);

            NotificationRequestKafkaDto notificationRequest = NotificationRequestKafkaDto.builder()
                    .userId(userId)
                    .notificationType(NotificationType.REGISTRATION_COMPLETE)
                    .params(params)
                    .attachWebAppButton(false)
                    .recipientTelegramId(null)
                    .build();

            log.debug("Отправка Kafka сообщения типа {} для пользователя ID {} в топик {}",
                    NotificationType.REGISTRATION_COMPLETE.name(), userId, notificationRequestTopic);

            CompletableFuture<SendResult<String, NotificationRequestKafkaDto>> future =
                    notificationKafkaTemplate.send(notificationRequestTopic, userId.toString(), notificationRequest);

            future.whenComplete((result, ex) -> {
                if (ex == null) {
                    log.info("Kafka сообщение (REGISTRATION_COMPLETE) для ID {} успешно отправлено (Offset: {})",
                            userId, result.getRecordMetadata().offset());
                } else {
                    log.error("Ошибка отправки Kafka (REGISTRATION_COMPLETE) для ID {}: {}",
                            userId, ex.getMessage(), ex);
                }
            });
        } catch (Exception e) {
            log.error("Ошибка при подготовке Kafka сообщения для уведомления о регистрации {}: {}",
                    username, e.getMessage(), e);
        }
    }

    private Authentication createAuthentication(String username, Set<Role> roles) {
        List<GrantedAuthority> authorities = roles.stream()
                .map(role -> new SimpleGrantedAuthority(role.getName().getRole()))
                .collect(Collectors.toList());
        return new UsernamePasswordAuthenticationToken(username, null, authorities);
    }

    private UserCredentialsResponse getUserProfileFromUserService(UUID userId, String requestRefreshTokenStr) {
        try {
            ResponseEntity<UserCredentialsResponse> responseEntity = userServiceClient.findUserById(userId);
            if (!responseEntity.getStatusCode().is2xxSuccessful() || responseEntity.getBody() == null) {
                log.error("User Service вернул ошибку или пустое тело для userId: {} при обновлении токена. Статус: {}",
                        userId, responseEntity.getStatusCode());
                throw new TokenRefreshException(requestRefreshTokenStr, "Ошибка получения данных пользователя для обновления токена.");
            }
            return responseEntity.getBody();
        } catch (FeignException e) {
            log.error("Ошибка Feign при запросе данных пользователя для userId: {} из User Service при обновлении токена: статус {}, тело {}",
                    userId, e.status(), e.contentUTF8(), e);
            throw new TokenRefreshException(requestRefreshTokenStr, "Ошибка связи с сервисом пользователей при обновлении токена.", e);
        } catch (Exception e) {
            log.error("Неожиданная ошибка при запросе данных пользователя для userId: {} из User Service при обновлении токена:", userId, e);
            throw new TokenRefreshException(requestRefreshTokenStr, "Внутренняя ошибка при обновлении токена.", e);
        }
    }

    private JwtResponse buildJwtResponse(String accessToken, RefreshToken refreshToken, UUID userId, Authentication authentication) {
        return JwtResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken.getToken())
                .userId(userId)
                .roles(authentication.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .collect(Collectors.toList()))
                .accessTokenExpiresInMs(appProperties.getJwtAccessExpirationMs())
                .refreshTokenExpiresInMs(appProperties.getJwtRefreshExpirationMs())
                .build();
    }
}
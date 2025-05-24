package ru.ecosharing.auth_service.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.ecosharing.auth_service.client.UserServiceClient;
import ru.ecosharing.auth_service.config.AppProperties;
import ru.ecosharing.auth_service.dto.NotificationRequestKafkaDto; // Используем DTO для Kafka
import ru.ecosharing.auth_service.dto.enums.TelegramAuthStatus;
import ru.ecosharing.auth_service.dto.enums.NotificationType;
import ru.ecosharing.auth_service.dto.request.AvailabilityCheckRequest;
import ru.ecosharing.auth_service.dto.request.CreateUserProfileRequest;
import ru.ecosharing.auth_service.dto.response.AvailabilityCheckResponse;
import ru.ecosharing.auth_service.dto.response.JwtResponse;
import ru.ecosharing.auth_service.dto.response.UserCredentialsResponse;
import ru.ecosharing.auth_service.dto.telegram.*;
import ru.ecosharing.auth_service.exception.*;
import ru.ecosharing.auth_service.model.*; // Импортируем локальный Enum NotificationType
import ru.ecosharing.auth_service.repository.RoleRepository;
import ru.ecosharing.auth_service.repository.UserCredentialsRepository;
import ru.ecosharing.auth_service.security.JwtTokenProvider;
import ru.ecosharing.auth_service.service.RefreshTokenService;
import ru.ecosharing.auth_service.service.TelegramAuthService;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Реализация сервиса аутентификации и регистрации через Telegram WebApp.
 * Использует Kafka для отправки запроса на уведомление.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramAuthServiceImpl implements TelegramAuthService {

    // --- Зависимости ---
    private final UserCredentialsRepository userCredentialsRepository;
    private final RoleRepository roleRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;
    private final AuthenticationManager authenticationManager;
    private final RefreshTokenService refreshTokenService;
    private final UserServiceClient userServiceClient;
    private final AppProperties appProperties;
    private final KafkaTemplate<String, NotificationRequestKafkaDto> notificationKafkaTemplate; // KafkaTemplate

    @Value("${kafka.topic.notification-requests}") // Имя топика из application.yml
    private String notificationRequestTopic;

    private static final long AUTH_DATA_TTL_SECONDS = 86400; // 24 часа

    /**
     * Аутентификация пользователя на основе данных initData из Telegram WebApp.
     */
    @Override
    @Transactional // Убираем readOnly = true, т.к. создается refresh токен
    public TelegramAuthResult authenticateTelegramUser(TelegramAuthRequest request) {
        log.debug("Попытка аутентификации через Telegram WebApp initData.");
        try {
            // 1. Парсинг и валидация initData
            Map<String, String> initDataMap = parseAndValidateInitData(request.getInitData());

            // 2. Извлечение данных пользователя Telegram
            Map<String, Object> telegramUserDataMap = objectMapper.readValue(
                    initDataMap.get("user"), new TypeReference<Map<String, Object>>() {});
            String telegramId = Optional.ofNullable(telegramUserDataMap.get("id"))
                    .map(Object::toString)
                    .orElseThrow(() -> new InvalidTelegramDataException("Отсутствует 'id' в данных пользователя Telegram."));
            log.debug("Проверка пользователя с Telegram ID: {}", telegramId);

            // 3. Поиск учетных данных по telegramId
            Optional<UserCredentials> credentialsOpt = userCredentialsRepository.findByTelegramId(telegramId);

            if (credentialsOpt.isPresent()) {
                // --- Пользователь найден ---
                UserCredentials credentials = credentialsOpt.get();
                if (!credentials.isActive()) { // Проверка локального статуса
                    throw new UserDeactivatedException("Учетная запись пользователя деактивирована.");
                }
                // Получение актуального username и проверка статуса в User Service
                UserCredentialsResponse userProfile = getUserProfileFromUserService(credentials.getUserId(), "telegram_auth");
                if (!userProfile.isActive()) { // Проверка статуса из User Service
                    throw new UserDeactivatedException("Учетная запись пользователя деактивирована.");
                }
                String username = userProfile.getUsername();
                log.info("Пользователь {} (Telegram ID: {}) найден. Генерация токенов.", username, telegramId);

                // Генерируем токены
                Authentication authentication = createAuthentication(username, credentials.getRoles());
                String accessToken = jwtTokenProvider.createAccessToken(authentication, credentials.getUserId());
                RefreshToken refreshToken = refreshTokenService.createRefreshToken(credentials.getUserId(), username); // Создаем/обновляем refresh токен
                JwtResponse jwtResponse = buildJwtResponse(accessToken, refreshToken, credentials.getUserId(), authentication);
                return new TelegramAuthResult(TelegramAuthStatus.SUCCESS, jwtResponse, null);

            } else {
                // --- Пользователь не найден ---
                log.info("Учетные данные для Telegram ID {} не найдены. Требуется вход или регистрация.", telegramId);
                TelegramUserData userData = new TelegramUserData(
                        telegramId,
                        telegramUserDataMap.getOrDefault("first_name", "").toString(),
                        telegramUserDataMap.getOrDefault("last_name", "").toString(),
                        telegramUserDataMap.getOrDefault("username", "").toString()
                );
                return new TelegramAuthResult(TelegramAuthStatus.AUTH_REQUIRED, null, userData);
            }
        } catch (InvalidTelegramDataException | UserDeactivatedException | AuthenticationProcessException e) {
            log.warn("Ошибка аутентификации Telegram: {}", e.getMessage());
            throw e; // Пробрасываем известные ошибки
        } catch (Exception e) {
            log.error("Неожиданная ошибка при аутентификации через Telegram: {}", e.getMessage(), e);
            throw new AuthenticationProcessException("Внутренняя ошибка при обработке данных Telegram.", e);
        }
    }

    /**
     * Вход существующего пользователя по логину/паролю с привязкой Telegram ID.
     */
    @Override
    @Transactional
    public JwtResponse loginWithTelegram(TelegramLoginRequest request) {
        log.info("Попытка входа пользователя {} и привязки Telegram ID {}", request.getUsername(), request.getTelegramId());

        // 1. Проверка, не занят ли Telegram ID другим пользователем
        UUID userId = findUserIdByLogin(request.getUsername());
        Optional<UserCredentials> existingTelegramUser = userCredentialsRepository.findByTelegramId(request.getTelegramId());
        if (existingTelegramUser.isPresent() && !existingTelegramUser.get().getUserId().equals(userId)) {
            throw new TelegramIdAlreadyBoundException("Этот Telegram аккаунт уже привязан к другому пользователю.");
        }

        // 2. Аутентификация по логину/паролю
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
            );
            UUID authUserId = findUserIdByLogin(request.getUsername());
            if (!userId.equals(authUserId)) { // Доп. проверка консистентности
                throw new AuthenticationProcessException("Ошибка несоответствия данных пользователя.");
            }
        } catch (BadCredentialsException | UsernameNotFoundException e) {
            throw new InvalidCredentialsException("Неверное имя пользователя или пароль.");
        } catch (Exception e) {
            Throwable cause = e.getCause();
            if (cause instanceof UserDeactivatedException) throw (UserDeactivatedException) cause;
            if (cause instanceof AuthenticationProcessException) throw (AuthenticationProcessException) cause;
            throw new AuthenticationProcessException("Произошла ошибка во время входа.", e);
        }

        // 3. Привязка Telegram ID
        UserCredentials credentials = userCredentialsRepository.findByUserId(userId)
                .orElseThrow(() -> new AuthenticationProcessException("Ошибка консистентности данных пользователя."));
        if (credentials.getTelegramId() == null || !credentials.getTelegramId().equals(request.getTelegramId())) {
            credentials.setTelegramId(request.getTelegramId());
            userCredentialsRepository.save(credentials);
            log.info("Telegram ID {} успешно привязан к пользователю {} (ID: {}).", request.getTelegramId(), request.getUsername(), userId);
        }

        // 4. Генерация токенов
        String accessToken = jwtTokenProvider.createAccessToken(authentication, userId);
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(userId, request.getUsername());

        // 5. Возврат ответа
        return buildJwtResponse(accessToken, refreshToken, userId, authentication);
    }

    /**
     * Регистрация нового пользователя через Telegram с вводом пароля.
     */
    @Override
    @Transactional
    public JwtResponse registerWithTelegram(TelegramRegisterRequest request) {
        log.info("Попытка регистрации нового пользователя {} с Telegram ID {}", request.getUsername(), request.getTelegramId());

        // 1. Проверка занятости Telegram ID
        if (userCredentialsRepository.existsByTelegramId(request.getTelegramId())) {
            throw new TelegramIdAlreadyBoundException("Этот Telegram аккаунт уже зарегистрирован.");
        }
        // 2. Проверка доступности username/email
        checkUserAvailability(request.getUsername(), request.getEmail());
        // 3. Генерация userId
        UUID newUserId = UUID.randomUUID();
        // 4. Определение ролей
        Set<Role> roles = determineUserRoles(request.getPassword());
        // 5. Создание и сохранение учетных данных
        UserCredentials credentials = UserCredentials.builder()
                .userId(newUserId)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .telegramId(request.getTelegramId())
                .isActive(true)
                .roles(roles)
                .build();
        userCredentialsRepository.save(credentials);
        log.info("Учетные данные для пользователя {} (ID: {}, TG_ID: {}) сохранены.", request.getUsername(), newUserId, request.getTelegramId());
        // 6. Создание профиля в User Service
        createProfileInUserService(newUserId, request);

        // 7. Отправка уведомления через Kafka
        sendWelcomeTelegramNotificationViaKafka(newUserId, request.getFirstName(), request.getUsername(), request.getTelegramId());

        // 8. Генерация токенов
        Authentication authentication = createAuthentication(request.getUsername(), roles);
        String accessToken = jwtTokenProvider.createAccessToken(authentication, newUserId);
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(newUserId, request.getUsername());
        log.info("Пользователь {} (ID: {}) успешно зарегистрирован через Telegram.", request.getUsername(), newUserId);
        // 9. Возврат ответа
        return buildJwtResponse(accessToken, refreshToken, newUserId, authentication);
    }

    // =========================================================================
    // --- Вспомогательные приватные методы ---
    // =========================================================================

    private UUID findUserIdByLogin(String login) {
        try {
            ResponseEntity<UserCredentialsResponse> response = userServiceClient.findUserByLogin(login);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new UsernameNotFoundException("Пользователь не найден с логином: " + login);
            }
            return response.getBody().getUserId();
        } catch (FeignException.NotFound e) {
            throw new UsernameNotFoundException("Пользователь не найден с логином: " + login);
        } catch (Exception e) {
            log.error("Ошибка при поиске userId по логину {} в User Service: {}", login, e.getMessage(), e);
            throw new AuthenticationProcessException("Ошибка получения данных пользователя.", e);
        }
    }

    private void checkUserAvailability(String username, String email) {
        try {
            AvailabilityCheckRequest checkRequest = new AvailabilityCheckRequest(username, email);
            ResponseEntity<AvailabilityCheckResponse> checkResponse = userServiceClient.checkAvailability(checkRequest);
            if (!checkResponse.getStatusCode().is2xxSuccessful() || checkResponse.getBody() == null) {
                throw new RegistrationException("Не удалось проверить доступность имени пользователя или email.");
            }
            AvailabilityCheckResponse availability = checkResponse.getBody();
            if (!availability.isUsernameAvailable()) {
                throw new UsernameAlreadyExistsException("Имя пользователя '" + username + "' уже занято.");
            }
            if (!availability.isEmailAvailable()) {
                throw new EmailAlreadyExistsException("Email '" + email + "' уже используется.");
            }
        } catch (FeignException e) {
            log.error("Ошибка Feign при проверке доступности (Telegram рег.): статус {}, тело {}", e.status(), e.contentUTF8(), e);
            throw new RegistrationException("Ошибка связи с сервисом пользователей при проверке данных.", e);
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

    private void createProfileInUserService(UUID userId, TelegramRegisterRequest request) {
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
            if (!profileResponse.getStatusCode().is2xxSuccessful()) {
                log.error("User Service не смог создать профиль для TG пользователя {} (ID: {}). Статус: {}. Откат.", request.getUsername(), userId, profileResponse.getStatusCode());
                throw new RegistrationException("Не удалось создать профиль пользователя. Регистрация отменена.");
            }
            log.info("Профиль для TG пользователя {} (ID: {}) создан в User Service.", request.getUsername(), userId);
        } catch (FeignException e) {
            log.error("Ошибка Feign при создании профиля TG пользователя {} (ID: {}): статус {}, тело {}. Откат.", request.getUsername(), userId, e.status(), e.contentUTF8(), e);
            throw new RegistrationException("Ошибка связи с сервисом пользователей при создании профиля. Регистрация отменена.", e);
        } catch (Exception e) {
            log.error("Неожиданная ошибка при создании профиля TG пользователя {} (ID: {}): {}. Откат.", request.getUsername(), userId, e.getMessage(), e);
            throw new RegistrationException("Внутренняя ошибка при создании профиля. Регистрация отменена.", e);
        }
    }

    /**
     * Отправляет запрос на приветственное уведомление через Kafka.
     */
    private void sendWelcomeTelegramNotificationViaKafka(UUID userId, String firstName, String username, String telegramId) {
        try {
            Map<String, String> params = new HashMap<>();
            params.put("firstName", firstName != null ? firstName : username);
            params.put("username", username);

            NotificationRequestKafkaDto notificationRequest = NotificationRequestKafkaDto.builder()
                    .userId(userId)
                    .recipientTelegramId(telegramId)
                    .notificationType(NotificationType.REGISTRATION_COMPLETE)
                    .params(params)
                    .attachWebAppButton(true) // Всегда прикрепляем кнопку при TG регистрации
                    .build();

            log.debug("Отправка Kafka сообщения типа {} для пользователя ID {} в топик {}",
                    NotificationType.REGISTRATION_COMPLETE, userId, notificationRequestTopic);

            CompletableFuture<SendResult<String, NotificationRequestKafkaDto>> future =
                    notificationKafkaTemplate.send(notificationRequestTopic, userId.toString(), notificationRequest);

            future.whenComplete((result, ex) -> {
                if (ex == null) {
                    log.info("Kafka сообщение для приветственного уведомления пользователя ID {} успешно отправлено (Offset: {})",
                            userId, result.getRecordMetadata().offset());
                } else {
                    log.error("Ошибка отправки Kafka сообщения для приветственного уведомления пользователя ID {}: {}",
                            userId, ex.getMessage(), ex);
                }
            });

        } catch (Exception e) {
            log.error("Ошибка при подготовке Kafka сообщения для приветственного уведомления пользователя {}: {}",
                    username, e.getMessage(), e);
        }
    }

    private Authentication createAuthentication(String username, Set<Role> roles) {
        List<GrantedAuthority> authorities = roles.stream()
                .map(role -> new SimpleGrantedAuthority(role.getName().getRole()))
                .collect(Collectors.toList());
        return new UsernamePasswordAuthenticationToken(username, null, authorities);
    }

    private UserCredentialsResponse getUserProfileFromUserService(UUID userId, String contextHint) {
        try {
            ResponseEntity<UserCredentialsResponse> response = userServiceClient.findUserById(userId);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new AuthenticationProcessException("Ошибка получения данных пользователя (context: " + contextHint + ").");
            }
            return response.getBody();
        } catch (FeignException e) {
            log.error("Ошибка Feign при запросе {} для userId {}: статус {}, тело {}", contextHint, userId, e.status(), e.contentUTF8(), e);
            throw new AuthenticationProcessException("Ошибка связи с сервисом пользователей (context: " + contextHint + ").", e);
        } catch (Exception e) {
            log.error("Неожиданная ошибка при запросе {} для userId {}:", contextHint, userId, e);
            throw new AuthenticationProcessException("Внутренняя ошибка при получении данных пользователя (context: " + contextHint + ").", e);
        }
    }

    private JwtResponse buildJwtResponse(String accessToken, RefreshToken refreshToken, UUID userId, Authentication authentication) {
        return JwtResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken.getToken())
                .userId(userId)
                .roles(authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.toList()))
                .accessTokenExpiresInMs(appProperties.getJwtAccessExpirationMs())
                .refreshTokenExpiresInMs(appProperties.getJwtRefreshExpirationMs())
                .build();
    }

    private Map<String, String> parseAndValidateInitData(String initData) throws InvalidTelegramDataException {
        if (initData == null || initData.isEmpty()) {
            throw new InvalidTelegramDataException("initData не может быть пустым.");
        }
        Map<String, String> dataMap = new HashMap<>();
        try {
            String[] pairs = initData.split("&");
            for (String pair : pairs) {
                int idx = pair.indexOf("=");
                if (idx > 0) {
                    String key = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
                    String value = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8);
                    dataMap.put(key, value);
                }
            }
        } catch (Exception e) {
            throw new InvalidTelegramDataException("Некорректный формат initData.");
        }
        if (!dataMap.containsKey("hash") || !dataMap.containsKey("auth_date") || !dataMap.containsKey("user")) {
            throw new InvalidTelegramDataException("В initData отсутствуют обязательные поля.");
        }
        if (!validateTelegramHash(dataMap)) {
            throw new InvalidTelegramDataException("Неверная подпись данных Telegram.");
        }
        try {
            long authDate = Long.parseLong(dataMap.get("auth_date"));
            long currentTime = Instant.now().getEpochSecond();
            if (currentTime - authDate > AUTH_DATA_TTL_SECONDS) {
                throw new InvalidTelegramDataException("Данные аутентификации Telegram устарели.");
            }
        } catch (NumberFormatException e) {
            throw new InvalidTelegramDataException("Некорректный формат даты аутентификации.");
        }
        log.debug("initData успешно распарсены и валидированы.");
        return dataMap;
    }

    private boolean validateTelegramHash(Map<String, String> dataMap) {
        String receivedHash = dataMap.get("hash");
        if (receivedHash == null) return false;
        final Set<String> fieldsToExclude = Set.of("hash");
        String dataCheckString = dataMap.entrySet().stream()
                .filter(entry -> !fieldsToExclude.contains(entry.getKey()))
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("\n"));
        try {
            Mac hmacSha256 = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpecForSecret = new SecretKeySpec("WebAppData".getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            hmacSha256.init(keySpecForSecret);
            byte[] secretKey = hmacSha256.doFinal(appProperties.getTelegramBotToken().getBytes(StandardCharsets.UTF_8));
            SecretKeySpec keySpecForData = new SecretKeySpec(secretKey, "HmacSHA256");
            hmacSha256.init(keySpecForData);
            byte[] calculatedHashBytes = hmacSha256.doFinal(dataCheckString.getBytes(StandardCharsets.UTF_8));
            StringBuilder calculatedHashHex = new StringBuilder();
            for (byte b : calculatedHashBytes) {
                calculatedHashHex.append(String.format("%02x", b));
            }
            boolean isValid = calculatedHashHex.toString().equalsIgnoreCase(receivedHash);
            if (!isValid) {
                log.warn("Ошибка валидации хеша Telegram. Ожидаемый: {}, Полученный: {}, Строка проверки:\n{}", calculatedHashHex, receivedHash, dataCheckString);
            }
            return isValid;
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("Критическая ошибка при вычислении хеша Telegram: {}", e.getMessage(), e);
            return false;
        }
    }
}
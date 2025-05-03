package ru.ecosharing.auth_service.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.ecosharing.auth_service.client.NotificationClient;
import ru.ecosharing.auth_service.client.UserServiceClient;
import ru.ecosharing.auth_service.config.AppProperties;
import ru.ecosharing.auth_service.dto.enumeration.NotificationType;
import ru.ecosharing.auth_service.dto.enumeration.TelegramAuthStatus;
import ru.ecosharing.auth_service.dto.request.*;
import ru.ecosharing.auth_service.dto.response.AvailabilityCheckResponse;
import ru.ecosharing.auth_service.dto.response.JwtResponse;
import ru.ecosharing.auth_service.dto.telegram.*;
import ru.ecosharing.auth_service.dto.response.UserCredentialsResponse;
import ru.ecosharing.auth_service.model.*;
import ru.ecosharing.auth_service.exception.*;
import ru.ecosharing.auth_service.repository.*;
import ru.ecosharing.auth_service.security.JwtTokenProvider;
import ru.ecosharing.auth_service.service.RefreshTokenService;
import ru.ecosharing.auth_service.service.TelegramAuthService;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant; // Для проверки времени
import java.util.*;
import java.util.stream.Collectors;

/**
 * Реализация сервиса аутентификации и регистрации через Telegram WebApp.
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
    private final ObjectMapper objectMapper; // Для парсинга JSON из initData
    private final AuthenticationManager authenticationManager; // Для loginWithTelegram
    private final RefreshTokenService refreshTokenService;
    private final UserServiceClient userServiceClient; // Для взаимодействия с User Service
    private final NotificationClient notificationClient;
    private final AppProperties appProperties;

    private static final long AUTH_DATA_TTL_SECONDS = 86400; // Время жизни данных initData (24 часа)

    /**
     * Аутентификация пользователя на основе данных initData из Telegram WebApp.
     */
    @Override
    @Transactional
    public TelegramAuthResult authenticateTelegramUser(TelegramAuthRequest request) {
        log.debug("Попытка аутентификации через Telegram WebApp initData.");
        try {
            // 1. Парсинг и валидация initData (подпись, время жизни)
            Map<String, String> initDataMap = parseAndValidateInitData(request.getInitData());

            // 2. Извлечение данных пользователя Telegram из initData
            Map<String, Object> telegramUserDataMap = objectMapper.readValue(
                    initDataMap.get("user"), new TypeReference<Map<String, Object>>() {});

            String telegramId = Optional.ofNullable(telegramUserDataMap.get("id"))
                    .map(Object::toString)
                    .orElseThrow(() -> new InvalidTelegramDataException("Отсутствует 'id' в данных пользователя Telegram."));

            log.debug("Проверка пользователя с Telegram ID: {}", telegramId);

            // 3. Поиск учетных данных по telegramId в локальной базе Auth Service
            Optional<UserCredentials> credentialsOpt = userCredentialsRepository.findByTelegramId(telegramId);

            if (credentialsOpt.isPresent()) {
                // --- Пользователь найден по Telegram ID ---
                UserCredentials credentials = credentialsOpt.get();

                // 4. Проверка локального статуса активности
                if (!credentials.isActive()) {
                    log.warn("Пользователь с Telegram ID {} деактивирован локально.", telegramId);
                    throw new UserDeactivatedException("Учетная запись пользователя деактивирована.");
                }

                // 5. Получение актуального username и проверка статуса в User Service
                UserCredentialsResponse userProfile = getUserProfileFromUserService(credentials.getUserId(), "telegram_auth");
                if (!userProfile.isActive()) {
                    log.warn("Пользователь userId: {} (Telegram ID: {}) деактивирован в User Service.", credentials.getUserId(), telegramId);
                    throw new UserDeactivatedException("Учетная запись пользователя деактивирована.");
                }
                String username = userProfile.getUsername();

                log.info("Пользователь {} (Telegram ID: {}) найден. Генерация токенов.", username, telegramId);

                // 6. Генерируем токены
                Authentication authentication = createAuthentication(username, credentials.getRoles());
                String accessToken = jwtTokenProvider.createAccessToken(authentication, credentials.getUserId());
                RefreshToken refreshToken = refreshTokenService.createRefreshToken(credentials.getUserId(), username);

                // 7. Формируем успешный ответ
                JwtResponse jwtResponse = buildJwtResponse(accessToken, refreshToken, credentials.getUserId(), authentication);
                return new TelegramAuthResult(TelegramAuthStatus.SUCCESS, jwtResponse, null);

            } else {
                // --- Пользователь с таким Telegram ID не найден ---
                log.info("Учетные данные для Telegram ID {} не найдены. Требуется вход или регистрация.", telegramId);

                // 8. Собираем данные из Telegram для передачи на фронтенд
                TelegramUserData userData = new TelegramUserData(
                        telegramId,
                        telegramUserDataMap.getOrDefault("first_name", "").toString(),
                        telegramUserDataMap.getOrDefault("last_name", "").toString(),
                        telegramUserDataMap.getOrDefault("username", "").toString()
                );
                // 9. Возвращаем результат с требованием дальнейших действий
                return new TelegramAuthResult(TelegramAuthStatus.AUTH_REQUIRED, null, userData);
            }

        } catch (InvalidTelegramDataException | UserDeactivatedException | AuthenticationProcessException e) {
            log.warn("Ошибка аутентификации Telegram: {}", e.getMessage());
            throw e; // Пробрасываем известные и обработанные ошибки
        } catch (Exception e) {
            // Ловим все остальные непредвиденные ошибки
            log.error("Неожиданная ошибка при аутентификации через Telegram: {}", e.getMessage(), e);
            throw new AuthenticationProcessException("Внутренняя ошибка при обработке данных Telegram.", e);
        }
    }

    /**
     * Вход существующего пользователя по логину/паролю с привязкой Telegram ID.
     */
    @Override
    @Transactional // Нужна транзакция для возможного сохранения telegramId
    public JwtResponse loginWithTelegram(TelegramLoginRequest request) {
        log.info("Попытка входа пользователя {} и привязки Telegram ID {}", request.getUsername(), request.getTelegramId());

        // 1. Проверка, не занят ли этот Telegram ID уже ДРУГИМ пользователем
        //    Сначала найдем userId по логину, затем проверим telegramId
        UUID userId = findUserIdByLogin(request.getUsername()); // Если не найден, выбросит исключение

        Optional<UserCredentials> existingTelegramUser = userCredentialsRepository.findByTelegramId(request.getTelegramId());
        if (existingTelegramUser.isPresent() && !existingTelegramUser.get().getUserId().equals(userId)) {
            log.warn("Telegram ID {} уже привязан к другому пользователю (ID: {}).",
                    request.getTelegramId(), existingTelegramUser.get().getUserId());
            throw new TelegramIdAlreadyBoundException("Этот Telegram аккаунт уже привязан к другому пользователю.");
        }

        // 2. Аутентификация по логину и паролю (как в обычном login)
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getUsername(), // UserDetailsServiceImpl использует username/email
                            request.getPassword()
                    )
            );
            // Дополнительно проверим, совпадает ли userId из аутентификации с найденным ранее
            UUID authUserId = findUserIdByLogin(request.getUsername());
            if (!userId.equals(authUserId)) {
                // Это очень странная ситуация
                log.error("Несоответствие UserId при входе с Telegram! Найденный ID: {}, ID после аутентификации: {}", userId, authUserId);
                throw new AuthenticationProcessException("Ошибка несоответствия данных пользователя.");
            }
        } catch (BadCredentialsException | UsernameNotFoundException e) {
            log.warn("Неудачная попытка входа для {} при привязке Telegram ID: {}", request.getUsername(), e.getMessage());
            throw new InvalidCredentialsException("Неверное имя пользователя или пароль.");
        } catch (Exception e) {
            log.error("Ошибка аутентификации для {} при привязке Telegram ID:", request.getUsername(), e);
            Throwable cause = e.getCause();
            if (cause instanceof UserDeactivatedException) throw (UserDeactivatedException) cause;
            if (cause instanceof AuthenticationProcessException) throw (AuthenticationProcessException) cause;
            throw new AuthenticationProcessException("Произошла ошибка во время входа.", e);
        }

        // 3. Находим учетные данные пользователя в Auth Service по userId
        UserCredentials credentials = userCredentialsRepository.findByUserId(userId)
                .orElseThrow(() -> {
                    log.error("КРИТИЧЕСКАЯ ОШИБКА: Учетные данные не найдены для пользователя ID {} после аутентификации!", userId);
                    return new AuthenticationProcessException("Ошибка консистентности данных пользователя.");
                });

        // 4. Привязываем Telegram ID (если еще не привязан или отличается) и сохраняем
        if (credentials.getTelegramId() == null || !credentials.getTelegramId().equals(request.getTelegramId())) {
            credentials.setTelegramId(request.getTelegramId());
            userCredentialsRepository.save(credentials);
            log.info("Telegram ID {} успешно привязан к пользователю {} (ID: {}).",
                    request.getTelegramId(), request.getUsername(), userId);
        } else {
            log.debug("Telegram ID {} уже был привязан к пользователю {} (ID: {}).",
                    request.getTelegramId(), request.getUsername(), userId);
        }

        // 5. Генерируем токены
        String accessToken = jwtTokenProvider.createAccessToken(authentication, userId);
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(userId, request.getUsername());

        // 6. Формируем ответ
        return buildJwtResponse(accessToken, refreshToken, userId, authentication);
    }


    /**
     * Регистрация нового пользователя через Telegram с вводом пароля.
     */
    @Override
    @Transactional // Обязательно транзакционно!
    public JwtResponse registerWithTelegram(TelegramRegisterRequest request) {
        log.info("Попытка регистрации нового пользователя {} с Telegram ID {}", request.getUsername(), request.getTelegramId());

        // 1. Проверка, не занят ли уже Telegram ID в Auth Service
        if (userCredentialsRepository.existsByTelegramId(request.getTelegramId())) {
            log.warn("Попытка регистрации с уже существующим Telegram ID: {}", request.getTelegramId());
            throw new TelegramIdAlreadyBoundException("Этот Telegram аккаунт уже зарегистрирован.");
        }

        // 2. Проверка доступности username и email через User Service
        checkUserAvailability(request.getUsername(), request.getEmail());

        // 3. Генерируем новый userId
        UUID newUserId = UUID.randomUUID();

        // 4. Определяем роли (с проверкой секретных паролей)
        Set<Role> roles = determineUserRoles(request.getPassword());

        // 5. Создаем и сохраняем учетные данные в Auth Service
        UserCredentials credentials = UserCredentials.builder()
                .userId(newUserId)
                .passwordHash(passwordEncoder.encode(request.getPassword())) // Хешируем введенный пароль
                .telegramId(request.getTelegramId()) // Сохраняем Telegram ID
                .isActive(true)
                .roles(roles)
                .build();
        userCredentialsRepository.save(credentials);
        log.info("Учетные данные для пользователя {} (ID: {}, TG_ID: {}) сохранены.",
                request.getUsername(), newUserId, request.getTelegramId());

        // 6. Создаем профиль пользователя в User Service
        createProfileInUserService(newUserId, request); // Используем перегруженный метод

        // 7. Отправка приветственного уведомления в Telegram
        sendWelcomeTelegramNotification(request.getTelegramId(), request.getFirstName());

        // 8. Генерируем токены
        Authentication authentication = createAuthentication(request.getUsername(), roles);
        String accessToken = jwtTokenProvider.createAccessToken(authentication, newUserId);
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(newUserId, request.getUsername());

        log.info("Пользователь {} (ID: {}) успешно зарегистрирован через Telegram.", request.getUsername(), newUserId);

        // 9. Возвращаем ответ с токенами
        return buildJwtResponse(accessToken, refreshToken, newUserId, authentication);
    }

    // =========================================================================
    // --- Вспомогательные приватные методы (аналогичные AuthServiceImpl + специфичные для Telegram) ---
    // =========================================================================

    /**
     * Находит UserId по логину (username или email), обращаясь к User Service.
     * Дублируется из AuthServiceImpl для локального использования.
     */
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
            log.error("Ошибка при поиске userId по логину {} в User Service:", login, e);
            throw new AuthenticationProcessException("Ошибка получения данных пользователя.");
        }
    }

    /**
     * Проверяет доступность username и email через User Service.
     * Дублируется из AuthServiceImpl.
     */
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
            log.error("Ошибка Feign при проверке доступности (Telegram рег.): {}", e.getMessage(), e);
            throw new RegistrationException("Ошибка связи с сервисом пользователей при проверке данных.", e);
        }
    }

    /**
     * Определяет набор ролей для нового пользователя на основе введенного пароля.
     * Дублируется из AuthServiceImpl.
     */
    private Set<Role> determineUserRoles(String rawPassword) {
        Set<Role> roles = new HashSet<>();
        roles.add(findRoleOrThrow(RoleName.ROLE_USER));
        if (rawPassword.equals(appProperties.getAdminSecretPassword())) {
            roles.add(findRoleOrThrow(RoleName.ROLE_ADMIN));
        } else if (rawPassword.equals(appProperties.getModeratorSecretPassword())) {
            roles.add(findRoleOrThrow(RoleName.ROLE_MODERATOR));
        }
        return roles;
    }

    /**
     * Находит роль по имени или выбрасывает исключение.
     * Дублируется из AuthServiceImpl.
     */
    private Role findRoleOrThrow(RoleName roleName) {
        return roleRepository.findByName(roleName)
                .orElseThrow(() -> new RegistrationException("Ошибка конфигурации: роль " + roleName + " не найдена."));
    }

    /**
     * Вызывает User Service для создания профиля (перегруженная версия для TelegramRegisterRequest).
     */
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
                log.error("User Service не смог создать профиль для TG пользователя {} (ID: {}). Статус: {}. Откат.",
                        request.getUsername(), userId, profileResponse.getStatusCode());
                throw new RegistrationException("Не удалось создать профиль пользователя. Регистрация отменена.");
            }
            log.info("Профиль для TG пользователя {} (ID: {}) создан в User Service.", request.getUsername(), userId);
        } catch (FeignException e) {
            log.error("Ошибка Feign при создании профиля TG пользователя {} (ID: {}): {}. Откат.",
                    request.getUsername(), userId, e.getMessage(), e);
            throw new RegistrationException("Ошибка связи с сервисом пользователей. Регистрация отменена.", e);
        } catch (Exception e) {
            log.error("Неожиданная ошибка при создании профиля TG пользователя {} (ID: {}): {}. Откат.",
                    request.getUsername(), userId, e.getMessage(), e);
            throw new RegistrationException("Внутренняя ошибка при создании профиля. Регистрация отменена.", e);
        }
    }

    /**
     * Отправляет приветственное уведомление в Telegram.
     */
    private void sendWelcomeTelegramNotification(String telegramId, String firstName) {
        try {
            Map<String, String> params = new HashMap<>();
            params.put("firstName", firstName != null ? firstName : "Пользователь");
            NotificationRequest notification = new NotificationRequest();
            notification.setChatId(telegramId); // Используем TG ID как Chat ID
            notification.setNotificationType(NotificationType.WELCOME_TELEGRAM);
            notification.setParams(params);
            notification.setAttachWebAppButton(true); // Предлагаем сразу открыть WebApp
            notificationClient.sendNotification(notification);
            log.info("Приветственное уведомление отправлено в Telegram для пользователя с TG ID: {}", telegramId);
        } catch (Exception e) {
            // Не критично для регистрации, просто логируем
            log.error("Не удалось отправить приветственное уведомление в Telegram для TG ID {}: {}", telegramId, e.getMessage(), e);
        }
    }

    /**
     * Создает объект Authentication для генерации токена.
     * Дублируется из AuthServiceImpl.
     */
    private Authentication createAuthentication(String username, Set<Role> roles) {
        List<GrantedAuthority> authorities = roles.stream()
                .map(role -> new SimpleGrantedAuthority(role.getName().getRole()))
                .collect(Collectors.toList());
        return new UsernamePasswordAuthenticationToken(username, null, authorities);
    }

    /**
     * Получает профиль пользователя из User Service по ID.
     * Используется при аутентификации через Telegram. Дублируется из AuthServiceImpl.
     */
    private UserCredentialsResponse getUserProfileFromUserService(UUID userId, String contextHint) {
        try {
            ResponseEntity<UserCredentialsResponse> response = userServiceClient.findUserById(userId);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.error("Не удалось получить данные из User Service для userId: {} (context: {}). Статус: {}",
                        userId, contextHint, response.getStatusCode());
                throw new AuthenticationProcessException("Ошибка получения данных пользователя (context: " + contextHint + ").");
            }
            return response.getBody();
        } catch (FeignException e) {
            log.error("Ошибка Feign при запросе данных пользователя для userId: {} (context: {}) из User Service: статус={}, тело={}",
                    userId, contextHint, e.status(), e.contentUTF8(), e);
            throw new AuthenticationProcessException("Ошибка связи с сервисом пользователей (context: " + contextHint + ").", e);
        } catch (Exception e) {
            log.error("Неожиданная ошибка при запросе данных пользователя для userId: {} (context: {}) из User Service:", userId, contextHint, e);
            throw new AuthenticationProcessException("Внутренняя ошибка при получении данных пользователя (context: " + contextHint + ").", e);
        }
    }

    /**
     * Собирает финальный JwtResponse DTO.
     * Дублируется из AuthServiceImpl.
     */
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


    /**
     * Парсит и валидирует строку initData из Telegram WebApp.
     * Проверяет подпись и время жизни данных.
     * @param initData Строка initData.
     * @return Map с распарсенными данными.
     * @throws InvalidTelegramDataException если данные некорректны.
     */
    private Map<String, String> parseAndValidateInitData(String initData) throws InvalidTelegramDataException {
        if (initData == null || initData.isEmpty()) {
            throw new InvalidTelegramDataException("initData не может быть пустым.");
        }

        Map<String, String> dataMap = new HashMap<>();
        try {
            // Парсим строку вида key1=value1&key2=value2...
            String[] pairs = initData.split("&");
            for (String pair : pairs) {
                int idx = pair.indexOf("=");
                if (idx > 0) {
                    // Декодируем ключ и значение
                    String key = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
                    String value = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8);
                    dataMap.put(key, value);
                }
            }
        } catch (Exception e) {
            log.error("Ошибка парсинга initData: {}", e.getMessage());
            throw new InvalidTelegramDataException("Некорректный формат initData.");
        }

        // Проверка наличия необходимых полей перед валидацией хеша
        if (!dataMap.containsKey("hash") || !dataMap.containsKey("auth_date") || !dataMap.containsKey("user")) {
            log.warn("В initData отсутствуют обязательные поля (hash, auth_date, user). Данные: {}", dataMap);
            throw new InvalidTelegramDataException("В initData отсутствуют обязательные поля.");
        }

        // Проверка подписи данных
        if (!validateTelegramHash(dataMap)) {
            log.warn("Неверная подпись данных Telegram: {}", dataMap);
            throw new InvalidTelegramDataException("Неверная подпись данных Telegram.");
        }

        // Проверка времени жизни данных (auth_date)
        try {
            long authDate = Long.parseLong(dataMap.get("auth_date"));
            long currentTime = Instant.now().getEpochSecond(); // Текущее время в секундах UTC
            if (currentTime - authDate > AUTH_DATA_TTL_SECONDS) {
                log.warn("Данные аутентификации Telegram устарели (auth_date: {}), TTL: {} сек.", authDate, AUTH_DATA_TTL_SECONDS);
                throw new InvalidTelegramDataException("Данные аутентификации Telegram устарели.");
            }
        } catch (NumberFormatException e) {
            log.warn("Некорректный формат auth_date в initData: {}", dataMap.get("auth_date"));
            throw new InvalidTelegramDataException("Некорректный формат даты аутентификации.");
        }

        log.debug("initData успешно распарсены и валидированы.");
        return dataMap;
    }

    /**
     * Валидирует хеш данных Telegram согласно официальной документации.
     * @param dataMap Map с данными из initData.
     * @return true, если хеш валиден, иначе false.
     */
    private boolean validateTelegramHash(Map<String, String> dataMap) {
        String receivedHash = dataMap.get("hash");
        // Если хеша нет, валидация невозможна
        if (receivedHash == null) {
            log.warn("Отсутствует 'hash' в данных Telegram для валидации.");
            return false;
        }

        final Set<String> fieldsToExclude = Set.of("hash");

        // Формируем строку для проверки data_check_string:
        // пары key=value, отсортированные по ключу, разделенные символом \n
        String dataCheckString = dataMap.entrySet().stream()
                .filter(entry -> !fieldsToExclude.contains(entry.getKey())) // Исключаем из строки проверки
                .sorted(Map.Entry.comparingByKey()) // Сортируем по ключу
                .map(entry -> entry.getKey() + "=" + entry.getValue()) // Формируем строку key=value
                .collect(Collectors.joining("\n")); // Объединяем через перевод строки

        try {
            // 1. Вычисляем секретный ключ: secret_key = HMAC_SHA256(<bot_token>, "WebAppData")
            Mac hmacSha256 = Mac.getInstance("HmacSHA256");
            // Ключ для HMAC - это токен бота
            SecretKeySpec keySpecForSecret = new SecretKeySpec("WebAppData".getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            hmacSha256.init(keySpecForSecret);
            // Данные для HMAC - строка "WebAppData"
            byte[] secretKey = hmacSha256.doFinal(appProperties.getTelegramBotToken().getBytes(StandardCharsets.UTF_8));

            // 2. Вычисляем хеш строки проверки: hash = HMAC_SHA256(data_check_string, secret_key)
            // Теперь ключ - это вычисленный secretKey
            SecretKeySpec keySpecForData = new SecretKeySpec(secretKey, "HmacSHA256");
            hmacSha256.init(keySpecForData);
            // Данные для HMAC - это dataCheckString
            byte[] calculatedHashBytes = hmacSha256.doFinal(dataCheckString.getBytes(StandardCharsets.UTF_8));

            // 3. Конвертируем вычисленный хеш в шестнадцатеричную строку
            StringBuilder calculatedHashHex = new StringBuilder();
            for (byte b : calculatedHashBytes) {
                calculatedHashHex.append(String.format("%02x", b));
            }

            // 4. Сравниваем вычисленный хеш с полученным хешом (без учета регистра)
            boolean isValid = calculatedHashHex.toString().equalsIgnoreCase(receivedHash);
            if (!isValid) {
                log.warn("Ошибка валидации хеша Telegram. Ожидаемый: {}, Полученный: {}, Строка проверки:\n{}",
                        calculatedHashHex, receivedHash, dataCheckString);
            } else {
                log.trace("Хеш Telegram успешно валидирован.");
            }
            return isValid;

        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            // Эти ошибки не должны возникать при стандартных реализациях Java
            log.error("Критическая ошибка при вычислении хеша Telegram: {}", e.getMessage(), e);
            return false;
        }
    }
}
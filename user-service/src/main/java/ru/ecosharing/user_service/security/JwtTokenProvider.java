package ru.ecosharing.user_service.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct; // Для инициализации после создания бина
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder; // Для статических методов доступа
import org.springframework.security.core.userdetails.User; // Spring Security User
import org.springframework.stereotype.Component;
import ru.ecosharing.user_service.config.JwtProperties; // Свойства с секретом

import java.nio.charset.StandardCharsets;
import java.security.Key; // Используем java.security.Key
import java.util.*;
import java.util.stream.Collectors;

/**
 * Компонент для валидации JWT токенов и извлечения из них данных аутентификации.
 * Использует секретный ключ, идентичный ключу в Auth Service.
 * НЕ СОЗДАЕТ ТОКЕНЫ.
 */
@Slf4j
@Component // Регистрируем как Spring бин
@RequiredArgsConstructor // Внедряем зависимости через конструктор
public class JwtTokenProvider {

    // Константы для имен claim'ов (должны совпадать с Auth Service)
    private static final String AUTHORITIES_KEY = "auth"; // Ключ для ролей
    private static final String USER_ID_KEY = "uid";     // Ключ для ID пользователя

    private final JwtProperties jwtProperties; // Свойства с секретным ключом
    private Key signingKey; // Ключ для валидации подписи

    /**
     * Инициализация секретного ключа после создания бина JwtTokenProvider.
     */
    @PostConstruct
    public void init() {
        try {
            byte[] keyBytes = jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8);
            this.signingKey = Keys.hmacShaKeyFor(keyBytes); // Создаем ключ для HMAC-SHA алгоритмов
            log.info("JWT ключ для валидации успешно инициализирован.");
        } catch (Exception e) {
            log.error("Ошибка инициализации JWT ключа! Убедитесь, что jwt.secret задан корректно.", e);
            // Можно выбросить исключение, чтобы приложение не стартовало без ключа
            // throw new RuntimeException("Ошибка инициализации JWT ключа", e);
        }
    }

    /**
     * Валидирует JWT токен (проверяет подпись и срок действия).
     * @param token Строка JWT токена.
     * @return true, если токен валиден, иначе false.
     */
    public boolean validateToken(String token) {
        if (this.signingKey == null) {
            log.error("JWT ключ не инициализирован! Валидация невозможна.");
            return false;
        }
        try {
            // Пытаемся распарсить токен. Если подпись неверна или срок истек, будет выброшено исключение.
            Jwts.parserBuilder()
                    .setSigningKey(signingKey) // Указываем ключ для проверки подписи
                    .build()
                    .parseClaimsJws(token); // Метод parseClaimsJws проверяет и подпись, и срок действия
            log.trace("JWT токен успешно валидирован.");
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            // Логируем распространенные ошибки валидации как trace или debug
            log.trace("Невалидный JWT токен: {}", e.getMessage());
        } catch (Exception e) {
            // Логируем другие непредвиденные ошибки
            log.error("Непредвиденная ошибка при валидации JWT токена: {}", e.getMessage(), e);
        }
        return false; // Если возникло любое исключение - токен невалиден
    }

    /**
     * Извлекает данные аутентификации из валидного JWT токена.
     * Вызывается из JwtAuthenticationFilter.
     * @param token Строка валидного JWT токена.
     * @return Объект Authentication, готовый для установки в SecurityContext, или null при ошибке.
     */
    public Authentication getAuthentication(String token) {
        if (this.signingKey == null) {
            log.error("JWT ключ не инициализирован! Невозможно извлечь аутентификацию.");
            return null;
        }
        try {
            // 1. Парсим токен и извлекаем все claims (уже проверили валидность в фильтре)
            Claims claims = extractAllClaims(token);

            // 2. Извлекаем роли из claim'а "auth"
            Collection<? extends GrantedAuthority> authorities = extractAuthorities(claims);

            // 3. Извлекаем username (из subject) и userId (из claim'а "uid")
            String username = claims.getSubject();
            UUID userId = extractUserId(claims);

            // Проверяем наличие обязательных данных
            if (username == null || userId == null) {
                log.warn("В токене отсутствуют обязательные claims: subject (username) или uid (userId). Claims: {}", claims);
                return null; // Не можем создать аутентификацию без этих данных
            }

            // 4. Создаем principal (объект User из Spring Security)
            // Пароль не используется и не известен, ставим заглушку ""
            User principal = new User(username, "", authorities);

            // 5. Создаем объект Authentication (UsernamePasswordAuthenticationToken)
            UsernamePasswordAuthenticationToken authenticationToken =
                    new UsernamePasswordAuthenticationToken(
                            principal, // Principal (тот, кто аутентифицирован)
                            token,     // Credentials (сам токен, может быть полезен)
                            authorities // Authorities (права/роли)
                    );

            // 6. Сохраняем userId в деталях Authentication для легкого доступа
            authenticationToken.setDetails(userId);

            log.trace("Аутентификация успешно извлечена из токена для пользователя '{}' (ID: {})", username, userId);
            return authenticationToken;

        } catch (JwtException | IllegalArgumentException e) {
            // Ошибки парсинга (хотя токен уже должен быть валидным на этом этапе)
            log.warn("Ошибка при извлечении данных из JWT: {}", e.getMessage());
            return null;
        } catch (Exception e) {
            log.error("Неожиданная ошибка при извлечении аутентификации из JWT: {}", e.getMessage(), e);
            return null;
        }
    }

    // --- Приватные вспомогательные методы ---

    /**
     * Извлекает все Claims (полезную нагрузку) из токена.
     * Не выполняет проверку срока действия или подписи здесь.
     */
    private Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(signingKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    /**
     * Извлекает ID пользователя (UUID) из Claims.
     */
    private UUID extractUserId(Claims claims) {
        String userIdStr = claims.get(USER_ID_KEY, String.class);
        if (userIdStr == null) {
            log.warn("Claim '{}' отсутствует в JWT.", USER_ID_KEY);
            return null;
        }
        try {
            return UUID.fromString(userIdStr);
        } catch (IllegalArgumentException e) {
            log.error("Некорректный формат UUID в claim '{}': {}", USER_ID_KEY, userIdStr, e);
            return null;
        }
    }

    /**
     * Извлекает коллекцию GrantedAuthority (ролей) из Claims.
     */
    private Collection<? extends GrantedAuthority> extractAuthorities(Claims claims) {
        String authoritiesString = claims.get(AUTHORITIES_KEY, String.class);
        if (authoritiesString == null || authoritiesString.trim().isEmpty()) {
            log.trace("Claim '{}' отсутствует или пуст в JWT.", AUTHORITIES_KEY);
            return Collections.emptyList(); // Возвращаем пустой список, если ролей нет
        }
        // Разделяем строку ролей по запятой и создаем SimpleGrantedAuthority
        return Arrays.stream(authoritiesString.split(","))
                .map(String::trim) // Убираем пробелы по краям
                .filter(auth -> !auth.isEmpty()) // Исключаем пустые строки после разделения
                .map(SimpleGrantedAuthority::new) // Создаем объект роли
                .collect(Collectors.toList());
    }


    // --- Статические утилитарные методы для доступа к данным текущего пользователя ---

    /**
     * Получает ID текущего аутентифицированного пользователя из SecurityContext.
     * @return Optional с UUID пользователя или пустой Optional, если пользователь не аутентифицирован
     *         или ID не был сохранен в деталях.
     */
    public static Optional<UUID> getCurrentUserId() {
        // Получаем текущий объект Authentication
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        // Проверяем, что аутентификация существует, она валидна и детали содержат UUID
        if (authentication != null && authentication.isAuthenticated() && authentication.getDetails() instanceof UUID) {
            return Optional.of((UUID) authentication.getDetails());
        }
        log.trace("Не удалось получить userId из SecurityContext. Authentication: {}", authentication);
        return Optional.empty();
    }

    /**
     * Получает список ролей текущего аутентифицированного пользователя из SecurityContext.
     * @return Список строковых представлений ролей или пустой список.
     */
    public static List<String> getCurrentUserRoles() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            // Получаем GrantedAuthority и преобразуем их в строки
            return authentication.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .collect(Collectors.toList());
        }
        return Collections.emptyList(); // Возвращаем пустой список, если нет аутентификации
    }

    /**
     * Получает имя (username) текущего аутентифицированного пользователя из SecurityContext.
     * @return Optional с именем пользователя или пустой Optional.
     */
    public static Optional<String> getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            // Principal обычно является объектом User или строкой с именем
            Object principal = authentication.getPrincipal();
            if (principal instanceof User) {
                return Optional.of(((User) principal).getUsername());
            } else if (principal instanceof String) {
                return Optional.of((String) principal);
            }
        }
        return Optional.empty();
    }
}

package ru.ecosharing.auth_service.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct; // Для инициализации ключа после создания бина
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User; // Spring Security User
import org.springframework.stereotype.Component;
import ru.ecosharing.auth_service.config.AppProperties; // Импорт свойств приложения

import java.nio.charset.StandardCharsets;
import java.security.Key; // Используем java.security.Key
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Компонент для создания, парсинга и валидации JWT токенов.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    // Константы для имен claim'ов в JWT
    private static final String AUTHORITIES_KEY = "auth"; // Ключ для хранения ролей
    private static final String USER_ID_KEY = "uid";     // Ключ для хранения ID пользователя

    private final AppProperties appProperties; // Доступ к конфигурационным свойствам (секрет, время жизни)
    private Key signingKey; // Секретный ключ для подписи и валидации токенов

    /**
     * Инициализация секретного ключа после создания бина.
     */
    @PostConstruct
    public void init() {
        byte[] keyBytes = appProperties.getJwtSecret().getBytes(StandardCharsets.UTF_8);
        // Создаем безопасный ключ для HMAC-SHA512
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        log.info("JWT ключ подписи инициализирован.");
    }

    /**
     * Создает Access Token на основе данных аутентификации.
     * @param authentication Объект Authentication с данными пользователя.
     * @param userId Уникальный ID пользователя.
     * @return Строка Access Token.
     */
    public String createAccessToken(Authentication authentication, UUID userId) {
        // Собираем роли пользователя в строку через запятую
        String authorities = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(","));

        Date now = new Date();
        // Вычисляем дату истечения срока действия access токена
        Date validity = new Date(now.getTime() + appProperties.getJwtAccessExpirationMs());

        // Строим JWT Access Token
        return Jwts.builder()
                .setSubject(authentication.getName()) // Устанавливаем username как subject
                .claim(AUTHORITIES_KEY, authorities) // Добавляем роли
                .claim(USER_ID_KEY, userId.toString()) // Добавляем userId
                .signWith(signingKey, SignatureAlgorithm.HS512) // Подписываем ключом и алгоритмом HS512
                .setIssuedAt(now) // Время выпуска
                .setExpiration(validity) // Время истечения
                .compact(); // Собираем токен
    }

    /**
     * Создает Refresh Token. Обычно содержит меньше информации, чем Access Token.
     * @param userId Уникальный ID пользователя.
     * @param username Имя пользователя (для subject).
     * @return Строка Refresh Token.
     */
    public String createRefreshToken(UUID userId, String username) {
        Date now = new Date();
        // Вычисляем дату истечения срока действия refresh токена
        Date validity = new Date(now.getTime() + appProperties.getJwtRefreshExpirationMs());

        // Строим JWT Refresh Token
        return Jwts.builder()
                .setSubject(username) // Используем username как subject
                .claim(USER_ID_KEY, userId.toString()) // Добавляем userId
                .signWith(signingKey, SignatureAlgorithm.HS512) // Подписываем тем же ключом/алгоритмом
                .setIssuedAt(now)
                .setExpiration(validity)
                .compact();
    }


    /**
     * Извлекает данные аутентификации из валидного JWT токена.
     * @param token Строка JWT токена.
     * @return Объект Authentication.
     */
    public Authentication getAuthentication(String token) {
        // Парсим токен и извлекаем все claims
        Claims claims = extractAllClaims(token);

        // Извлекаем роли из claim'а и преобразуем их в GrantedAuthority
        Collection<? extends GrantedAuthority> authorities =
                Arrays.stream(claims.get(AUTHORITIES_KEY, String.class).split(","))
                        .filter(auth -> !auth.trim().isEmpty()) // Фильтруем пустые строки
                        .map(SimpleGrantedAuthority::new) // Создаем SimpleGrantedAuthority
                        .collect(Collectors.toList());

        // Создаем объект User (из Spring Security) в качестве principal
        // Пароль здесь не нужен, используем пустую строку
        User principal = new User(claims.getSubject(), "", authorities);

        // Создаем объект UsernamePasswordAuthenticationToken, который реализует Authentication
        return new UsernamePasswordAuthenticationToken(principal, token, authorities);
    }

    /**
     * Проверяет валидность JWT токена (подпись и срок действия).
     * @param token Строка JWT токена.
     * @return true, если токен валиден, иначе false.
     */
    public boolean validateToken(String token) {
        try {
            // Пытаемся распарсить токен с проверкой подписи и срока действия
            Jwts.parserBuilder()
                    .setSigningKey(signingKey) // Указываем наш ключ
                    .build()
                    .parseClaimsJws(token); // Если парсинг прошел без исключений - токен валиден
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            // Логируем только как trace, т.к. невалидные токены - частое явление
            log.trace("Невалидный JWT токен: {}", e.getMessage());
        } catch (Exception e) {
            // Логируем другие возможные ошибки
            log.error("Ошибка валидации JWT токена", e);
        }
        return false; // Если было исключение, токен невалиден
    }

    /**
     * Извлекает имя пользователя (subject) из токена.
     * @param token Строка JWT токена.
     * @return Имя пользователя.
     */
    public String getUsernameFromToken(String token) {
        return extractAllClaims(token).getSubject();
    }

    /**
     * Извлекает ID пользователя из токена.
     * @param token Строка JWT токена.
     * @return UUID пользователя или null при ошибке.
     */
    public UUID getUserIdFromToken(String token) {
        String userIdStr = extractAllClaims(token).get(USER_ID_KEY, String.class);
        if (userIdStr == null) return null;
        try {
            return UUID.fromString(userIdStr);
        } catch (IllegalArgumentException e) {
            log.error("Некорректный формат UUID в JWT ({}): {}", USER_ID_KEY, userIdStr, e);
            return null;
        }
    }

    /**
     * Извлекает дату истечения срока действия токена.
     * @param token Строка JWT токена.
     * @return Дата истечения.
     */
    public Date getExpirationDateFromToken(String token) {
        return extractAllClaims(token).getExpiration();
    }

    /**
     * Приватный метод для парсинга токена и извлечения всех claims.
     */
    private Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(signingKey)
                .build()
                .parseClaimsJws(token)
                .getBody(); // Получаем тело (claims)
    }
}

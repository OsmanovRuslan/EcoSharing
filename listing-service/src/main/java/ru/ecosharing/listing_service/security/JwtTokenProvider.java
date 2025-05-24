package ru.ecosharing.listing_service.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.stereotype.Component;
import ru.ecosharing.listing_service.config.JwtProperties;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    private static final String AUTHORITIES_KEY = "auth"; // Ключ для ролей в токене
    private static final String USER_ID_KEY = "uid";     // Ключ для ID пользователя в токене

    private final JwtProperties jwtProperties;
    private Key signingKey;

    @PostConstruct
    public void init() {
        try {
            byte[] keyBytes = jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8);
            this.signingKey = Keys.hmacShaKeyFor(keyBytes);
            log.info("JWT ключ для валидации успешно инициализирован в Listing Service.");
        } catch (Exception e) {
            log.error("Ошибка инициализации JWT ключа в Listing Service! Убедитесь, что jwt.secret задан.", e);
            // Рассмотрите возможность выбросить здесь RuntimeException, чтобы приложение не стартовало без ключа.
        }
    }

    public boolean validateToken(String token) {
        if (this.signingKey == null) {
            log.error("JWT ключ не инициализирован! Валидация невозможна.");
            return false;
        }
        try {
            Jwts.parserBuilder().setSigningKey(signingKey).build().parseClaimsJws(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.trace("Невалидный JWT токен: {}", e.getMessage());
        } catch (Exception e) {
            log.error("Непредвиденная ошибка при валидации JWT токена: {}", e.getMessage(), e);
        }
        return false;
    }

    public Authentication getAuthentication(String token) {
        if (this.signingKey == null) {
            log.error("JWT ключ не инициализирован! Невозможно извлечь аутентификацию.");
            return null;
        }
        try {
            Claims claims = Jwts.parserBuilder().setSigningKey(signingKey).build().parseClaimsJws(token).getBody();

            String username = claims.getSubject();
            UUID userId = extractUserId(claims);

            if (username == null || userId == null) {
                log.warn("В токене отсутствуют обязательные claims: subject (username) или uid (userId). Claims: {}", claims);
                return null;
            }

            Collection<? extends GrantedAuthority> authorities =
                    Arrays.stream(claims.get(AUTHORITIES_KEY, String.class).split(","))
                            .filter(auth -> !auth.trim().isEmpty())
                            .map(SimpleGrantedAuthority::new)
                            .collect(Collectors.toList());

            User principal = new User(username, "", authorities);
            UsernamePasswordAuthenticationToken authenticationToken =
                    new UsernamePasswordAuthenticationToken(principal, token, authorities);

            // Сохраняем userId в деталях Authentication для легкого доступа в контроллерах/сервисах
            authenticationToken.setDetails(userId);
            return authenticationToken;

        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Ошибка при извлечении данных из JWT: {}", e.getMessage());
            return null;
        } catch (Exception e) {
            log.error("Неожиданная ошибка при извлечении аутентификации из JWT: {}", e.getMessage(), e);
            return null;
        }
    }

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

    // Статический метод для удобного получения ID текущего пользователя из SecurityContext
    public static Optional<UUID> getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() && authentication.getDetails() instanceof UUID) {
            return Optional.of((UUID) authentication.getDetails());
        }
        return Optional.empty();
    }
}
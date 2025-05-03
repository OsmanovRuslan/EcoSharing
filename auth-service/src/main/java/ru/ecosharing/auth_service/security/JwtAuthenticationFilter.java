package ru.ecosharing.auth_service.security;


import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils; // Утилита для работы со строками
import org.springframework.web.filter.OncePerRequestFilter; // Гарантирует выполнение фильтра один раз за запрос

import java.io.IOException;

/**
 * Фильтр, который перехватывает каждый запрос, проверяет наличие JWT токена в заголовке Authorization,
 * валидирует его и устанавливает аутентификацию пользователя в SecurityContext, если токен валиден.
 */
@Slf4j
@RequiredArgsConstructor // Генерирует конструктор для final поля jwtTokenProvider
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider; // Провайдер для работы с JWT

    private static final String AUTHORIZATION_HEADER = "Authorization"; // Имя заголовка
    private static final String BEARER_PREFIX = "Bearer "; // Префикс токена

    /**
     * Основной метод фильтра, выполняющийся для каждого запроса.
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        try {
            // 1. Извлекаем токен из заголовка
            String jwt = resolveToken(request);

            // 2. Проверяем, что токен есть и валиден
            if (StringUtils.hasText(jwt) && jwtTokenProvider.validateToken(jwt)) {
                // 3. Если токен валиден, получаем объект Authentication
                Authentication authentication = jwtTokenProvider.getAuthentication(jwt);
                if (authentication != null) {
                    // 4. Устанавливаем объект Authentication в SecurityContext
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    log.trace("Установлена аутентификация для пользователя '{}' в SecurityContext.", authentication.getName());
                } else {
                    log.warn("Не удалось создать объект Authentication из валидного токена.");
                    // Очищаем контекст на случай, если там что-то было
                    SecurityContextHolder.clearContext();
                }
            } else {
                // Если токена нет или он невалиден, просто очищаем контекст
                // SecurityConfig далее решит, разрешен ли доступ к ресурсу без аутентификации
                SecurityContextHolder.clearContext();
                log.trace("JWT токен отсутствует или невалиден для пути: {}", request.getRequestURI());
            }
        } catch (Exception e) {
            // Ловим возможные ошибки при обработке токена
            log.error("Ошибка при обработке JWT токена: {}", e.getMessage(), e);
            SecurityContextHolder.clearContext(); // Очищаем контекст при любой ошибке
        }

        // 5. Передаем запрос дальше по цепочке фильтров
        filterChain.doFilter(request, response);
    }

    /**
     * Извлекает JWT токен из заголовка Authorization.
     * @param request HttpServletRequest.
     * @return Строка токена без префикса "Bearer " или null, если токен не найден.
     */
    private String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader(AUTHORIZATION_HEADER);
        // Проверяем, что заголовок есть, не пустой и начинается с "Bearer "
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(BEARER_PREFIX)) {
            // Возвращаем сам токен (без префикса)
            return bearerToken.substring(BEARER_PREFIX.length());
        }
        // Если заголовок некорректный или отсутствует
        return null;
    }
}

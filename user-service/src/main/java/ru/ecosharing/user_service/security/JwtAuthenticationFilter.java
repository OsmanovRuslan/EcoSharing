package ru.ecosharing.user_service.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull; // Аннотация для указания non-null параметров
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext; // Импорт SecurityContext
import org.springframework.security.core.context.SecurityContextHolder; // Импорт SecurityContextHolder
import org.springframework.util.StringUtils; // Утилита для работы со строками
import org.springframework.web.filter.OncePerRequestFilter; // Гарантирует выполнение фильтра один раз за запрос

import java.io.IOException;

/**
 * Фильтр Spring Security, который проверяет наличие и валидность JWT токена
 * в заголовке Authorization каждого входящего запроса.
 * Если токен валиден, извлекает данные аутентификации и устанавливает их
 * в SecurityContextHolder.
 */
@Slf4j
// НЕ используем @Component, т.к. бин создается явно в SecurityConfig
@RequiredArgsConstructor // Внедряем зависимости через конструктор
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider; // Провайдер для работы с JWT

    private static final String AUTHORIZATION_HEADER = "Authorization"; // Имя HTTP заголовка
    private static final String BEARER_PREFIX = "Bearer "; // Стандартный префикс для JWT

    /**
     * Основной метод фильтрации. Выполняется для каждого запроса.
     * @param request HttpServletRequest
     * @param response HttpServletResponse
     * @param filterChain Цепочка фильтров
     * @throws ServletException Если возникает ошибка сервлета.
     * @throws IOException Если возникает ошибка ввода-вывода.
     */
    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request, // Используем @NonNull для подсказок IDE
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        final String authHeader = request.getHeader(AUTHORIZATION_HEADER);
        final String jwt;

        // 1. Проверяем наличие заголовка и префикса Bearer
        if (!StringUtils.hasText(authHeader) || !authHeader.startsWith(BEARER_PREFIX)) {
            log.trace("Заголовок Authorization отсутствует или не начинается с Bearer для пути: {}", request.getRequestURI());
            // Если заголовка нет, просто передаем управление дальше.
            // SecurityConfig решит, нужен ли токен для этого эндпоинта.
            filterChain.doFilter(request, response);
            return;
        }

        // 2. Извлекаем сам токен
        jwt = authHeader.substring(BEARER_PREFIX.length());

        // 3. Получаем текущий SecurityContext (если он уже есть, например, от предыдущего фильтра)
        // Обычно SecurityContext пуст на этом этапе для stateless приложения.
        SecurityContext context = SecurityContextHolder.getContext();

        // 4. Проверяем, что пользователь еще не аутентифицирован в текущем контексте
        //    и токен валиден (проверяем подпись и срок действия)
        if (context.getAuthentication() == null && jwtTokenProvider.validateToken(jwt)) {
            log.trace("Токен валиден, попытка извлечь аутентификацию.");
            try {
                // 5. Если токен валиден, извлекаем из него объект Authentication
                Authentication authentication = jwtTokenProvider.getAuthentication(jwt);

                if (authentication != null) {
                    // 6. Устанавливаем извлеченный объект Authentication в SecurityContext
                    // Теперь Spring Security знает, что пользователь аутентифицирован
                    context.setAuthentication(authentication);
                    SecurityContextHolder.setContext(context); // Обновляем контекст
                    log.trace("Аутентификация для '{}' успешно установлена в SecurityContext.", authentication.getName());
                } else {
                    // Случай, когда токен валиден, но создать Authentication не удалось (редко)
                    log.warn("Не удалось создать объект Authentication из валидного токена.");
                    SecurityContextHolder.clearContext(); // Очищаем на всякий случай
                }
            } catch (Exception e) {
                // Ловим ошибки, которые могли возникнуть при извлечении Authentication
                log.error("Ошибка при установке аутентификации из JWT: {}", e.getMessage(), e);
                SecurityContextHolder.clearContext(); // Очищаем контекст при ошибке
            }
        } else {
            if (context.getAuthentication() != null) {
                log.trace("Пользователь уже аутентифицирован, пропускаем JWT фильтр.");
            } else {
                log.trace("JWT токен не прошел валидацию.");
                SecurityContextHolder.clearContext(); // Очищаем, если токен был, но невалиден
            }
        }

        // 7. Передаем управление следующему фильтру в цепочке
        filterChain.doFilter(request, response);
    }
}
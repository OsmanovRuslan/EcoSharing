package ru.ecosharing.notification_service.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders; // Для константы AUTHORIZATION
import org.springframework.lang.NonNull; // Для аннотации @NonNull
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder; // Реактивный контекст
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange; // Реактивный аналог HttpServletRequest/Response
import org.springframework.web.server.WebFilter; // Интерфейс реактивного фильтра
import org.springframework.web.server.WebFilterChain; // Цепочка реактивных фильтров
import reactor.core.publisher.Mono; // Основной тип в Reactor
import reactor.util.context.Context; // Контекст Reactor


/**
 * Реактивный WebFilter для аутентификации по JWT токену.
 * Извлекает токен из заголовка Authorization, валидирует его с помощью JwtTokenProvider
 * и устанавливает объект Authentication в реактивный SecurityContext.
 */
@Slf4j
@RequiredArgsConstructor // Внедряем JwtTokenProvider
// @Component // Регистрировать как бин можно здесь или в SecurityConfig
public class ReactiveJwtAuthenticationFilter implements WebFilter {

    private final JwtTokenProvider jwtTokenProvider;

    private static final String BEARER_PREFIX = "Bearer ";

    /**
     * Метод фильтрации для каждого входящего запроса.
     * @param exchange ServerWebExchange содержит запрос и ответ.
     * @param chain WebFilterChain для передачи запроса дальше.
     * @return Mono<Void> сигнализирует о завершении обработки фильтра.
     */
    @Override
    @NonNull
    public Mono<Void> filter(@NonNull ServerWebExchange exchange, @NonNull WebFilterChain chain) {
        // 1. Извлекаем токен из заголовка запроса
        String token = resolveToken(exchange);

        // 2. Проверяем наличие и валидность токена
        if (StringUtils.hasText(token) && jwtTokenProvider.validateToken(token)) {
            // 3. Если токен валиден, получаем объект Authentication
            Authentication authentication = jwtTokenProvider.getAuthentication(token);
            if (authentication != null) {
                log.trace("Реактивный фильтр: Установка аутентификации для {}", authentication.getName());
                // 4. Устанавливаем Authentication в реактивный контекст и передаем дальше по цепочке
                //    Context.of(SecurityContext.class, Mono.just(new SecurityContextImpl(authentication))) - старый способ
                //    ReactiveSecurityContextHolder.withAuthentication(authentication) - новый, более удобный способ
                Context context = ReactiveSecurityContextHolder.withAuthentication(authentication);
                // chain.filter(exchange).contextWrite(context) - применяем контекст ко всей последующей цепочке
                return chain.filter(exchange).contextWrite(context);
            } else {
                log.warn("Реактивный фильтр: Не удалось создать Authentication из валидного токена.");
                // Не меняем контекст, передаем дальше как есть
                return chain.filter(exchange);
            }
        } else {
            log.trace("Реактивный фильтр: Токен не найден или невалиден для пути {}", exchange.getRequest().getPath());
            // Токен не найден или не валиден, просто передаем запрос дальше без изменения контекста
            return chain.filter(exchange);
        }
        // Примечание: В отличие от сервлетного фильтра, здесь не нужно явно вызывать SecurityContextHolder.clearContext(),
        // так как контекст создается для каждого запроса и не сохраняется между ними по умолчанию
        // (благодаря NoOpServerSecurityContextRepository).
    }

    /**
     * Извлекает токен из заголовка Authorization в ServerWebExchange.
     * @param exchange Реактивный обмен запросом/ответом.
     * @return Строка токена или null.
     */
    private String resolveToken(ServerWebExchange exchange) {
        // Получаем значение заголовка Authorization
        String bearerToken = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        // Проверяем и извлекаем токен
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(BEARER_PREFIX)) {
            return bearerToken.substring(BEARER_PREFIX.length());
        }

        return null; // Токен не найден
    }
}

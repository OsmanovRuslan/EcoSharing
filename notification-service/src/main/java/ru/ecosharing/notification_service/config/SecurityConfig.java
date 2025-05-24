package ru.ecosharing.notification_service.config; // Убедись, что пакет правильный

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod; // Для разрешений по методам
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity; // Используем HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer; // Для отключения csrf
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
// Импортируем фильтр и точку входа для Servlet API
import ru.ecosharing.notification_service.security.JwtAuthenticationEntryPoint;
import ru.ecosharing.notification_service.security.JwtAuthenticationFilter;
import ru.ecosharing.notification_service.security.JwtTokenProvider;

/**
 * Конфигурация безопасности Spring Security для Notification Service (на базе Spring MVC).
 */
@Configuration
@EnableWebSecurity // Включает веб-безопасность для Servlet стека
@EnableMethodSecurity(prePostEnabled = true, securedEnabled = true) // Для @PreAuthorize
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final JwtTokenProvider jwtTokenProvider;

    /**
     * Бин для нашего кастомного JWT фильтра.
     */
    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter() {
        return new JwtAuthenticationFilter(jwtTokenProvider); // Предполагаем, что этот фильтр подходит для Servlet API
    }

    /**
     * Настраивает цепочку фильтров безопасности и правила доступа для HTTP запросов.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable) // Отключаем CSRF
                // CORS теперь будет управляться API Gateway, здесь не настраиваем или разрешаем всё для простоты, если шлюз всё равно фильтрует
                // .cors(Customizer.withDefaults()) // Или настроить через CorsConfigurationSource бин
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(jwtAuthenticationEntryPoint) // Обработка 401
                )
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS) // Stateless приложение
                )
                .authorizeHttpRequests(auth -> auth
                                // Эндпоинт для Kafka Consumer'а не должен быть доступен извне,
                                // он не является HTTP эндпоинтом.
                                // Эндпоинт для SSE удален.
                                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll() // Разрешаем OPTIONS для CORS preflight
                                .requestMatchers("/api/notifications/my/**").authenticated() // API для фронта требует аутентификации
                                .requestMatchers("/actuator/**").permitAll() // Actuator
                                .anyRequest().denyAll() // Все остальные запросы запрещены по умолчанию
                        // (если есть другие эндпоинты, их нужно явно разрешить)
                );

        // Добавляем наш JWT фильтр перед стандартным фильтром Spring
        http.addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
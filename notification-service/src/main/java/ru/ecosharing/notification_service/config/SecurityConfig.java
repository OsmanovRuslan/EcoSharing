package ru.ecosharing.notification_service.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod; // Для разрешения OPTIONS
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity; // Для @PreAuthorize
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer; // Для disable()
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import ru.ecosharing.notification_service.security.JwtAuthenticationEntryPoint; // Точка входа для ошибок 401
import ru.ecosharing.notification_service.security.JwtAuthenticationFilter;     // Фильтр для JWT
import ru.ecosharing.notification_service.security.JwtTokenProvider;        // Для создания фильтра

/**
 * Конфигурация безопасности Spring Security для Notification Service.
 * Настраивает stateless аутентификацию по JWT для API фронтенда
 * и определяет правила доступа к эндпоинтам.
 */
@Configuration
@EnableWebSecurity // Включает веб-безопасность Spring Security
@EnableMethodSecurity(prePostEnabled = true, securedEnabled = true) // Включает аннотации @PreAuthorize
@RequiredArgsConstructor // Внедрение зависимостей через конструктор
public class SecurityConfig {

    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint; // Обработчик 401
    private final JwtTokenProvider jwtTokenProvider; // Провайдер для создания фильтра

    /**
     * Создает бин фильтра JWT аутентификации.
     */
    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter() {
        return new JwtAuthenticationFilter(jwtTokenProvider);
    }

    /**
     * Настраивает цепочку фильтров безопасности HTTP.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 1. Отключаем CSRF (не нужен для stateless API)
                .csrf(AbstractHttpConfigurer::disable)
                // 2. Отключаем стандартную обработку CORS в этом сервисе!
                //    Предполагается, что CORS полностью управляется API Gateway.
                .cors(AbstractHttpConfigurer::disable)
                // 3. Настраиваем обработку ошибок аутентификации
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(jwtAuthenticationEntryPoint) // Кастомный ответ 401
                )
                // 4. Устанавливаем политику управления сессиями на STATELESS
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                // 5. Настраиваем правила авторизации запросов
                .authorizeHttpRequests(auth -> auth
                        // Разрешаем preflight запросы OPTIONS для всех путей (важно для CORS через шлюз)
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // Разрешаем доступ к эндпоинтам Actuator (для мониторинга)
                        .requestMatchers("/actuator/**").permitAll()
                        // Все остальные запросы к API уведомлений (/api/notifications/**)
                        // требуют аутентификации. Конкретные права (например, что пользователь
                        // может читать только свои уведомления) проверяются в контроллере/сервисе
                        // с использованием @PreAuthorize или вручную через SecurityContext.
                        .requestMatchers("/api/notifications/**").authenticated()
                        // Все остальные запросы (если они есть) также требуют аутентификации
                        .anyRequest().authenticated()
                );

        // 6. Добавляем наш фильтр JWT перед стандартным фильтром Spring
        http.addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // AuthenticationManager и PasswordEncoder здесь не нужны,
    // так как сервис не выполняет аутентификацию по паролю.
    // Валидация токена происходит в JwtAuthenticationFilter.
}
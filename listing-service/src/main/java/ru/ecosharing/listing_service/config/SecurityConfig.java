package ru.ecosharing.listing_service.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import ru.ecosharing.listing_service.security.JwtAuthenticationEntryPoint;
import ru.ecosharing.listing_service.security.JwtAuthenticationFilter;
import ru.ecosharing.listing_service.security.JwtTokenProvider;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true, securedEnabled = true) // Для @PreAuthorize и @Secured
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final JwtTokenProvider jwtTokenProvider; // Нужен для создания JwtAuthenticationFilter

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter() {
        return new JwtAuthenticationFilter(jwtTokenProvider);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable) // Отключаем CSRF, т.к. используем JWT
                .cors(AbstractHttpConfigurer::disable) // Предполагаем, что CORS настроен на API Gateway
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(jwtAuthenticationEntryPoint) // Обработчик ошибок 401
                )
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS) // Не создаем HTTP сессии
                )
                .authorizeHttpRequests(auth -> auth
                        // Публичные эндпоинты (просмотр объявлений и категорий)
                        .requestMatchers(HttpMethod.GET, "/api/listings").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/listings/{listingId}").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/categories/**").permitAll()

                        // Эндпоинты, требующие аутентификации для всех пользователей
                        .requestMatchers(HttpMethod.POST, "/api/listings/my").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/listings/my/**").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/listings/my/{listingId}").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/listings/my/{listingId}").authenticated()
                        .requestMatchers(HttpMethod.PATCH, "/api/listings/my/{listingId}/activate").authenticated()
                        .requestMatchers(HttpMethod.PATCH, "/api/listings/my/{listingId}/deactivate").authenticated()
                        .requestMatchers("/api/listings/favorites/**").authenticated()

                        // Эндпоинты для модераторов/администраторов
                        .requestMatchers("/api/moderation/**").hasAnyRole("MODERATOR", "ADMIN")
                        // Если есть админские эндпоинты для категорий
                        .requestMatchers(HttpMethod.POST, "/api/categories").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/categories/{categoryId}").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/categories/{categoryId}").hasRole("ADMIN")

                        // Actuator (можно ограничить доступ в продакшене)
                        .requestMatchers("/actuator/**").permitAll()

                        // Все остальные запросы требуют аутентификации
                        .anyRequest().authenticated()
                );

        // Добавляем наш JWT фильтр перед стандартным фильтром Spring Security
        http.addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
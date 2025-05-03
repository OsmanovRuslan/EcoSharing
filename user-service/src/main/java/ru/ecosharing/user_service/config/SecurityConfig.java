package ru.ecosharing.user_service.config;

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
import ru.ecosharing.user_service.security.JwtAuthenticationEntryPoint;
import ru.ecosharing.user_service.security.JwtAuthenticationFilter;
import ru.ecosharing.user_service.security.JwtTokenProvider; // Нужен для фильтра


@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true, securedEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final JwtTokenProvider jwtTokenProvider; // Нужен для создания фильтра

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter() {
        // Создаем фильтр здесь, так как он зависит от JwtTokenProvider
        return new JwtAuthenticationFilter(jwtTokenProvider);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                )
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .authorizeHttpRequests(auth -> auth
                        // --- Публичные или требующие только аутентификации ---
                        .requestMatchers("/api/users/{userId}/public").permitAll() // Публичный профиль доступен всем
                        .requestMatchers("/api/users/me/**").authenticated() // Доступ к своему профилю и настройкам - любой аутентифицированный
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // --- Эндпоинты для Администраторов ---
                        .requestMatchers("/api/admin/**").hasRole("ADMIN") // Все админские эндпоинты требуют роль ADMIN

                        // --- Внутренние эндпоинты (для Auth Service) ---
                        // ВАЖНО: В реальном приложении эти эндпоинты должны быть защищены!
                        // Например, через IP-фильтрацию, mTLS или специальный внутренний токен/роль.
                        // Здесь для простоты оставляем их доступными без аутентификации (ПОД УГРОЗОЙ!)
                        // или можно требовать базовую аутентификацию/спец. токен, передаваемый Auth Service.
                        // Давайте пока оставим их открытыми, но с комментарием о небезопасности.
                        .requestMatchers("/api/internal/**").permitAll() // !!! НЕБЕЗОПАСНО В ПРОДАКШЕНЕ !!!

                        // --- Остальное ---
                        .requestMatchers("/actuator/**").permitAll() // Доступ к Actuator
                        .anyRequest().authenticated() // Все остальные требуют аутентификации по умолчанию
                );

        http.addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // AuthenticationManager здесь не нужен, т.к. user-service не выполняет аутентификацию по паролю
    // PasswordEncoder здесь тоже не нужен
}

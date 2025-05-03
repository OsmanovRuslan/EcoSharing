package ru.ecosharing.auth_service.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import ru.ecosharing.auth_service.dto.response.ErrorResponse; // DTO для ошибки

import java.io.IOException;
import java.time.LocalDateTime;

/**
 * Компонент для обработки ошибок аутентификации (401 Unauthorized).
 * Формирует JSON ответ вместо стандартной страницы ошибки Spring Security.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper; // Для сериализации DTO в JSON

    /**
     * Вызывается, когда неаутентифицированный пользователь пытается получить доступ к защищенному ресурсу.
     */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException, ServletException {
        log.warn("Ошибка аутентификации: '{}' для запроса: {}", authException.getMessage(), request.getRequestURI());

        // Формируем DTO с ошибкой
        ErrorResponse errorResponse = ErrorResponse.builder()
                .status(HttpServletResponse.SC_UNAUTHORIZED) // 401
                .message("Требуется аутентификация: " + authException.getMessage())
                .path(request.getRequestURI())
                .timestamp(LocalDateTime.now())
                .build();

        // Устанавливаем тип контента и статус ответа
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);

        // Записываем JSON в тело ответа
        objectMapper.writeValue(response.getOutputStream(), errorResponse);
    }
}

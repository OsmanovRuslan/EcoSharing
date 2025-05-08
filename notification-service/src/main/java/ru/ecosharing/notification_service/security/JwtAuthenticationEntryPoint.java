package ru.ecosharing.notification_service.security;

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
import ru.ecosharing.notification_service.dto.ErrorResponse;

import java.io.IOException;
import java.time.LocalDateTime;

/**
 * Компонент, который обрабатывает ошибки аутентификации (когда доступ запрещен из-за отсутствия
 * валидных учетных данных). Он вызывается Spring Security, когда требуется аутентификация,
 * но она не предоставлена или неверна (до этапа авторизации).
 * Формирует JSON-ответ со статусом 401 Unauthorized.
 */
@Slf4j
@Component // Регистрируем как Spring бин
@RequiredArgsConstructor // Внедряем ObjectMapper
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper; // Для преобразования DTO в JSON

    /**
     * Метод, вызываемый Spring Security при ошибке аутентификации.
     * @param request        Запрос, вызвавший ошибку.
     * @param response       Ответ, куда будет записана ошибка.
     * @param authException Исключение, содержащее детали ошибки аутентификации.
     * @throws IOException      Если произошла ошибка записи в response.
     * @throws ServletException Если произошла ошибка сервлета.
     */
    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException)
            throws IOException, ServletException {

        // Логируем ошибку аутентификации
        log.warn("Ошибка аутентификации для пути [{}]: {}", request.getRequestURI(), authException.getMessage());
        log.debug("Детали ошибки аутентификации:", authException); // Полное исключение в debug

        // Формируем стандартизированный DTO ответа об ошибке
        ErrorResponse errorResponse = ErrorResponse.builder()
                .status(HttpServletResponse.SC_UNAUTHORIZED) // 401
                .message("Ошибка аутентификации: Требуется действительный JWT токен.") // Общее сообщение
                // .message("Ошибка аутентификации: " + authException.getMessage()) // Можно включить детали, но осторожно
                .path(request.getRequestURI()) // Путь, где произошла ошибка
                .timestamp(LocalDateTime.now())
                .build();

        // Настраиваем HTTP ответ
        response.setContentType(MediaType.APPLICATION_JSON_VALUE); // Тип контента - JSON
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED); // Статус 401

        // Записываем JSON в тело ответа с помощью ObjectMapper
        objectMapper.writeValue(response.getOutputStream(), errorResponse);
    }
}
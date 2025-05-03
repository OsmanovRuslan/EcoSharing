package ru.ecosharing.auth_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Стандартизированный DTO для ответов об ошибках.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErrorResponse {
    private int status; // HTTP статус код
    private String message; // Сообщение об ошибке
    private String path; // Путь запроса, где произошла ошибка
    private LocalDateTime timestamp; // Время возникновения ошибки
    private Map<String, String> validationErrors; // Опционально: детали ошибок валидации по полям
}
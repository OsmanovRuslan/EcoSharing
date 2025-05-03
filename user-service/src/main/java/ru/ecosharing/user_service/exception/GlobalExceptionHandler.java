package ru.ecosharing.user_service.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException; // Ошибка авторизации (403 Forbidden)
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException; // Ошибка валидации DTO (@Valid)
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import ru.ecosharing.user_service.dto.response.ErrorResponse; // DTO для ответа об ошибке

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Глобальный обработчик исключений для всех REST контроллеров в User Service.
 * Перехватывает различные типы исключений и формирует стандартизированный JSON ответ.
 */
@Slf4j // Логгер Lombok
@RestControllerAdvice // Позволяет классу обрабатывать исключения глобально для контроллеров
public class GlobalExceptionHandler {

    /**
     * Обрабатывает ошибки валидации DTO, помеченных аннотацией @Valid.
     * Возвращает статус 400 Bad Request с деталями ошибок по полям.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationExceptions(
            MethodArgumentNotValidException ex, WebRequest request) {
        Map<String, String> errors = new HashMap<>();
        // Извлекаем сообщения об ошибках для каждого невалидного поля
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        // Формируем DTO ответа
        ErrorResponse errorResponse = ErrorResponse.builder()
                .status(HttpStatus.BAD_REQUEST.value()) // 400
                .message("Ошибка валидации входных данных") // Общее сообщение
                .path(getRequestPath(request)) // Путь запроса
                .timestamp(LocalDateTime.now())
                .validationErrors(errors) // Добавляем карту ошибок по полям
                .build();
        // Логируем как предупреждение, т.к. это ошибка на стороне клиента
        log.warn("Ошибка валидации для запроса [{}]: {}", getRequestPath(request), errors);
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    /**
     * Обрабатывает исключение ResourceNotFoundException (ресурс не найден).
     * Возвращает статус 404 Not Found.
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFoundException(
            ResourceNotFoundException ex, WebRequest request) {
        // Используем статус из аннотации @ResponseStatus(HttpStatus.NOT_FOUND) на исключении
        return createErrorResponse(ex, HttpStatus.NOT_FOUND, request);
    }

    /**
     * Обрабатывает исключение AccessDeniedException (доступ запрещен).
     * Возникает при проверке прав (например, @PreAuthorize).
     * Возвращает статус 403 Forbidden.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(
            AccessDeniedException ex, WebRequest request) {
        // Логируем как ошибку, т.к. это потенциальная проблема безопасности или логики
        log.error("Доступ запрещен для запроса [{}]: {}", getRequestPath(request), ex.getMessage());
        return createErrorResponse(ex, HttpStatus.FORBIDDEN, request);
    }

    /**
     * Обрабатывает общее исключение UserServiceException.
     * Статус берется из аннотации @ResponseStatus на исключении (по умолчанию 500),
     * но может быть и другим, если исключение унаследовано и аннотировано иначе.
     */
    @ExceptionHandler(UserServiceException.class)
    public ResponseEntity<ErrorResponse> handleUserServiceException(
            UserServiceException ex, WebRequest request) {
        // Получаем статус из аннотации @ResponseStatus на классе исключения
        ResponseStatus responseStatus = ex.getClass().getAnnotation(ResponseStatus.class);
        HttpStatus status = (responseStatus != null) ? responseStatus.value() : HttpStatus.INTERNAL_SERVER_ERROR;
        // Логируем как ошибку, если статус 500, или как предупреждение для других статусов
        if (status == HttpStatus.INTERNAL_SERVER_ERROR) {
            log.error("Ошибка User Service для запроса [{}]: {}", getRequestPath(request), ex.getMessage(), ex.getCause());
        } else {
            log.warn("Обработана ошибка User Service [{}] для запроса [{}]: {}", status, getRequestPath(request), ex.getMessage());
        }
        return createErrorResponse(ex, status, request);
    }

    /**
     * Обрабатывает все остальные непредвиденные исключения.
     * Возвращает статус 500 Internal Server Error.
     * Важно логировать полное исключение для диагностики.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGlobalException(Exception ex, WebRequest request) {
        // Логируем как ERROR с полным стектрейсом
        log.error("Непредвиденная внутренняя ошибка сервера для запроса [{}]:", getRequestPath(request), ex);
        // Формируем ответ для клиента без деталей исключения
        ErrorResponse errorResponse = ErrorResponse.builder()
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .message("Внутренняя ошибка сервера. Пожалуйста, попробуйте позже или свяжитесь с поддержкой.") // Общее сообщение
                .path(getRequestPath(request))
                .timestamp(LocalDateTime.now())
                .build();
        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    // --- Вспомогательные методы ---

    /**
     * Создает стандартизированный объект ResponseEntity<ErrorResponse> на основе исключения и статуса.
     * Логирует ошибку как WARN (можно настроить уровень в зависимости от статуса).
     * @param ex Исключение.
     * @param status HTTP статус.
     * @param request Текущий веб-запрос.
     * @return ResponseEntity с телом ErrorResponse.
     */
    private ResponseEntity<ErrorResponse> createErrorResponse(Exception ex, HttpStatus status, WebRequest request) {
        ErrorResponse errorResponse = ErrorResponse.builder()
                .status(status.value())
                .message(ex.getMessage()) // Используем сообщение из исключения
                .path(getRequestPath(request)) // Получаем путь запроса
                .timestamp(LocalDateTime.now())
                .build();
        // Логируем как WARN для ожидаемых ошибок приложения (4xx, некоторые 5xx)
        // Для критических можно использовать ERROR внутри конкретного хендлера
        log.warn("Обработано исключение [{} {}] для запроса [{}]: {}",
                status.value(), status.getReasonPhrase(), getRequestPath(request), ex.getMessage());
        return new ResponseEntity<>(errorResponse, status);
    }

    /**
     * Извлекает путь запроса из объекта WebRequest.
     * @param request Текущий веб-запрос.
     * @return Строка с путем запроса (например, "/api/users/me").
     */
    private String getRequestPath(WebRequest request) {
        // request.getDescription(false) обычно возвращает "uri=/api/users/me"
        try {
            return request.getDescription(false).replace("uri=", "");
        } catch (Exception e) {
            log.error("Не удалось получить путь запроса из WebRequest", e);
            return "unknown";
        }
    }
}
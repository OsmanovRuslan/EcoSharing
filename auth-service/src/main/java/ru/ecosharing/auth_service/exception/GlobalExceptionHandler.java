package ru.ecosharing.auth_service.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException; // Ошибка авторизации (403)
import org.springframework.security.core.AuthenticationException; // Общий класс ошибок аутентификации (401)
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException; // Ошибка валидации DTO (400)
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import ru.ecosharing.auth_service.dto.response.ErrorResponse; // Наш DTO для ответа об ошибке

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Глобальный обработчик исключений для всех контроллеров в Auth Service.
 * Перехватывает различные типы исключений и формирует стандартизированный JSON ответ.
 */
@Slf4j
@RestControllerAdvice // Аннотация для глобального обработчика исключений в REST контроллерах
public class GlobalExceptionHandler {

    /**
     * Обрабатывает ошибки валидации DTO, помеченных аннотацией @Valid.
     * Возвращает статус 400 Bad Request с деталями ошибок по полям.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationExceptions(
            MethodArgumentNotValidException ex, WebRequest request) {
        Map<String, String> errors = new HashMap<>();
        // Собираем все ошибки валидации по полям
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        ErrorResponse errorResponse = ErrorResponse.builder()
                .status(HttpStatus.BAD_REQUEST.value()) // 400
                .message("Ошибка валидации входных данных") // Общее сообщение
                .path(getRequestPath(request)) // Путь запроса
                .timestamp(LocalDateTime.now())
                .validationErrors(errors) // Добавляем карту ошибок по полям
                .build();
        log.warn("Ошибка валидации для запроса [{}]: {}", getRequestPath(request), errors);
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    /**
     * Обрабатывает общие ошибки аутентификации Spring Security (кроме InvalidCredentials и UserDeactivated).
     * Возвращает статус 401 Unauthorized.
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationException(AuthenticationException ex, WebRequest request) {
        // Исключаем наши кастомные подклассы, которые обрабатываются отдельно
        if (ex instanceof InvalidCredentialsException || ex instanceof UserDeactivatedException || ex instanceof AuthenticationProcessException) {
            // Эти исключения будут обработаны другими хендлерами
            throw ex; // Перебрасываем для обработки специфичным хендлером
        }
        // Обрабатываем остальные AuthenticationException (например, проблемы с UserDetailsService)
        log.warn("Общая ошибка аутентификации для запроса [{}]: {}", getRequestPath(request), ex.getMessage());
        return createErrorResponse(ex, HttpStatus.UNAUTHORIZED, request);
    }

    /**
     * Обрабатывает ошибки авторизации (недостаточно прав).
     * Возвращает статус 403 Forbidden.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(AccessDeniedException ex, WebRequest request) {
        log.warn("Доступ запрещен для запроса [{}]: {}", getRequestPath(request), ex.getMessage());
        return createErrorResponse(ex, HttpStatus.FORBIDDEN, request);
    }

    // --- Обработка наших кастомных исключений ---

    /**
     * Обрабатывает исключения, соответствующие статусу 400 Bad Request.
     */
    @ExceptionHandler({
            RegistrationException.class,
            InvalidTelegramDataException.class
    })
    public ResponseEntity<ErrorResponse> handleBadRequestExceptions(RuntimeException ex, WebRequest request) {
        return createErrorResponse(ex, HttpStatus.BAD_REQUEST, request);
    }

    /**
     * Обрабатывает исключения, соответствующие статусу 401 Unauthorized.
     */
    @ExceptionHandler({
            InvalidCredentialsException.class
    })
    public ResponseEntity<ErrorResponse> handleUnauthorizedExceptions(RuntimeException ex, WebRequest request) {
        // Здесь можно залогировать как INFO или DEBUG, т.к. это ожидаемое поведение при неверном пароле
        log.info("Неверные учетные данные для запроса [{}]: {}", getRequestPath(request), ex.getMessage());
        return createErrorResponse(ex, HttpStatus.UNAUTHORIZED, request);
    }

    /**
     * Обрабатывает исключения, соответствующие статусу 403 Forbidden.
     */
    @ExceptionHandler({
            TokenRefreshException.class,
            UserDeactivatedException.class
    })
    public ResponseEntity<ErrorResponse> handleForbiddenExceptions(RuntimeException ex, WebRequest request) {
        return createErrorResponse(ex, HttpStatus.FORBIDDEN, request);
    }

    /**
     * Обрабатывает исключения, соответствующие статусу 409 Conflict.
     */
    @ExceptionHandler({
            UsernameAlreadyExistsException.class,
            EmailAlreadyExistsException.class,
            TelegramIdAlreadyBoundException.class
    })
    public ResponseEntity<ErrorResponse> handleConflictExceptions(RuntimeException ex, WebRequest request) {
        return createErrorResponse(ex, HttpStatus.CONFLICT, request);
    }

    /**
     * Обрабатывает исключения, соответствующие статусу 500 Internal Server Error.
     */
    @ExceptionHandler({
            AuthenticationProcessException.class // Ошибки процесса аутентификации (например, связи)
    })
    public ResponseEntity<ErrorResponse> handleInternalServerExceptions(RuntimeException ex, WebRequest request) {
        // Логируем как ERROR, так как это проблемы на стороне сервера
        log.error("Ошибка процесса аутентификации/регистрации для запроса [{}]: {}", getRequestPath(request), ex.getMessage(), ex.getCause());
        return createErrorResponse(ex, HttpStatus.INTERNAL_SERVER_ERROR, request);
    }

    /**
     * Обрабатывает все остальные непредвиденные исключения.
     * Возвращает статус 500 Internal Server Error.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGlobalException(Exception ex, WebRequest request) {
        // Логируем как ERROR с полным стектрейсом
        log.error("Непредвиденная ошибка для запроса [{}]:", getRequestPath(request), ex);
        ErrorResponse errorResponse = ErrorResponse.builder()
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .message("Внутренняя ошибка сервера.") // Не показываем детали исключения пользователю
                .path(getRequestPath(request))
                .timestamp(LocalDateTime.now())
                .build();
        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    // --- Вспомогательные методы ---

    /**
     * Создает стандартный объект ResponseEntity<ErrorResponse> на основе исключения и статуса.
     */
    private ResponseEntity<ErrorResponse> createErrorResponse(Exception ex, HttpStatus status, WebRequest request) {
        ErrorResponse errorResponse = ErrorResponse.builder()
                .status(status.value())
                .message(ex.getMessage()) // Используем сообщение из исключения
                .path(getRequestPath(request))
                .timestamp(LocalDateTime.now())
                .build();
        // Логируем как WARN для ожидаемых ошибок приложения
        log.warn("Обработано исключение [{}] для запроса [{}]: {}", status, getRequestPath(request), ex.getMessage());
        return new ResponseEntity<>(errorResponse, status);
    }

    /**
     * Получает путь запроса из WebRequest.
     */
    private String getRequestPath(WebRequest request) {
        // request.getDescription(false) возвращает строку типа "uri=/api/auth/login"
        return request.getDescription(false).replace("uri=", "");
    }
}
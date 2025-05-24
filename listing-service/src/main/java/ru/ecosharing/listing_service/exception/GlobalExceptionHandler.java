package ru.ecosharing.listing_service.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import ru.ecosharing.listing_service.dto.response.ErrorResponse;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationExceptions(
            MethodArgumentNotValidException ex, WebRequest request) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        ErrorResponse errorResponse = ErrorResponse.builder()
                .status(HttpStatus.BAD_REQUEST.value())
                .message("Ошибка валидации входных данных")
                .path(getRequestPath(request))
                .timestamp(LocalDateTime.now())
                .validationErrors(errors)
                .build();
        log.warn("Ошибка валидации для запроса [{}]: {}", getRequestPath(request), errors);
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFoundException(
            ResourceNotFoundException ex, WebRequest request) {
        return createErrorResponse(ex, HttpStatus.NOT_FOUND, request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(
            AccessDeniedException ex, WebRequest request) {
        log.warn("Доступ запрещен для запроса [{}]: {}", getRequestPath(request), ex.getMessage());
        return createErrorResponse(ex, HttpStatus.FORBIDDEN, request);
    }

    @ExceptionHandler({ListingOperationException.class, CategoryOperationException.class})
    public ResponseEntity<ErrorResponse> handleBusinessLogicExceptions(
            RuntimeException ex, WebRequest request) {
        // Для этих исключений мы используем статус из аннотации @ResponseStatus на самом исключении (BAD_REQUEST)
        ResponseStatus responseStatus = ex.getClass().getAnnotation(ResponseStatus.class);
        HttpStatus status = (responseStatus != null) ? responseStatus.value() : HttpStatus.INTERNAL_SERVER_ERROR;
        return createErrorResponse(ex, status, request);
    }

    @ExceptionHandler(IllegalArgumentException.class) // Часто возникает из-за неверных входных данных
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(
            IllegalArgumentException ex, WebRequest request) {
        log.warn("Недопустимый аргумент для запроса [{}]: {}", getRequestPath(request), ex.getMessage());
        return createErrorResponse(ex, HttpStatus.BAD_REQUEST, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGlobalException(Exception ex, WebRequest request) {
        log.error("Непредвиденная внутренняя ошибка сервера для запроса [{}]:", getRequestPath(request), ex);
        ErrorResponse errorResponse = ErrorResponse.builder()
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .message("Внутренняя ошибка сервера. Пожалуйста, попробуйте позже.")
                .path(getRequestPath(request))
                .timestamp(LocalDateTime.now())
                .build();
        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private ResponseEntity<ErrorResponse> createErrorResponse(Exception ex, HttpStatus status, WebRequest request) {
        ErrorResponse errorResponse = ErrorResponse.builder()
                .status(status.value())
                .message(ex.getMessage())
                .path(getRequestPath(request))
                .timestamp(LocalDateTime.now())
                .build();
        if (status.is5xxServerError()) {
            log.error("Ошибка [{}] для запроса [{}]: {}", status, getRequestPath(request), ex.getMessage(), ex);
        } else {
            log.warn("Ошибка [{}] для запроса [{}]: {}", status, getRequestPath(request), ex.getMessage());
        }
        return new ResponseEntity<>(errorResponse, status);
    }

    private String getRequestPath(WebRequest request) {
        try {
            return request.getDescription(false).replace("uri=", "");
        } catch (Exception e) {
            log.error("Не удалось получить путь запроса из WebRequest", e);
            return "unknown_path";
        }
    }
}
package ru.ecosharing.user_service.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.UUID;

/**
 * Исключение, выбрасываемое, когда запрашиваемый ресурс (например, пользователь, адрес) не найден.
 * Соответствует HTTP статусу 404 Not Found.
 */
@ResponseStatus(HttpStatus.NOT_FOUND) // Устанавливаем HTTP статус по умолчанию
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public ResourceNotFoundException(String resourceName, String fieldName, Object fieldValue) {
        super(String.format("%s не найден(а) с %s : '%s'", resourceName, fieldName, fieldValue));
    }

    // Удобные статические фабричные методы
    public static ResourceNotFoundException userById(UUID userId) {
        return new ResourceNotFoundException("Пользователь", "ID", userId);
    }

    public static ResourceNotFoundException userByUsername(String username) {
        return new ResourceNotFoundException("Пользователь", "username", username);
    }

    public static ResourceNotFoundException userByEmail(String email) {
        return new ResourceNotFoundException("Пользователь", "email", email);
    }

    public static ResourceNotFoundException addressById(UUID addressId) {
        return new ResourceNotFoundException("Адрес", "ID", addressId);
    }

    public static ResourceNotFoundException settingsByUserId(UUID userId) {
        return new ResourceNotFoundException("Настройки пользователя", "userID", userId);
    }
}
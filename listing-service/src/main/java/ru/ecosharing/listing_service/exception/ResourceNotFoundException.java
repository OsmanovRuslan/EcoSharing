package ru.ecosharing.listing_service.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.UUID;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public ResourceNotFoundException(String resourceName, String fieldName, Object fieldValue) {
        super(String.format("%s не найден(а) с %s : '%s'", resourceName, fieldName, fieldValue));
    }

    // Фабричные методы для удобства
    public static ResourceNotFoundException listingById(UUID listingId) {
        return new ResourceNotFoundException("Объявление", "ID", listingId.toString());
    }

    public static ResourceNotFoundException categoryById(UUID categoryId) {
        return new ResourceNotFoundException("Категория", "ID", categoryId.toString());
    }

    public static ResourceNotFoundException favorite(UUID userId, UUID listingId) {
        return new ResourceNotFoundException("Запись в избранном", "userId/listingId", userId.toString() + "/" + listingId.toString());
    }
}
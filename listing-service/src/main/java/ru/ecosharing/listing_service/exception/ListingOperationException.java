package ru.ecosharing.listing_service.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST) // Часто такие ошибки являются результатом неверного запроса
public class ListingOperationException extends RuntimeException {

    public ListingOperationException(String message) {
        super(message);
    }

    public ListingOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}
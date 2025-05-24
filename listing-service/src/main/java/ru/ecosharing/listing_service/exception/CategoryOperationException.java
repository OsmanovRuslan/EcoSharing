package ru.ecosharing.listing_service.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class CategoryOperationException extends RuntimeException {

    public CategoryOperationException(String message) {
        super(message);
    }

    public CategoryOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}
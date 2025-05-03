package ru.ecosharing.auth_service.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO для запроса к User Service на проверку доступности username и email.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AvailabilityCheckRequest {
    private String username;
    private String email;
}
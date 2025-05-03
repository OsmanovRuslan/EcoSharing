package ru.ecosharing.auth_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO для ответа от User Service о доступности username и email.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AvailabilityCheckResponse {
    private boolean usernameAvailable; // true, если username свободен
    private boolean emailAvailable;    // true, если email свободен
}
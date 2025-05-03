package ru.ecosharing.user_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * DTO для представления краткой информации о пользователе (например, в админских списках).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSummaryResponse {

    private UUID userId;
    private String username;
    private String fullName;
    private String email; // Email часто нужен в админке
    private String avatarUrl;
    private Double rating; // Основной рейтинг
    private LocalDateTime createdAt;
    private boolean isActive;
    // Роли могут быть добавлены из SecurityContext, если нужно отображать их в списке
    private List<String> roles;
}
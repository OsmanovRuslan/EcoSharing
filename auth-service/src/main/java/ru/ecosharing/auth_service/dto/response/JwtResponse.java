package ru.ecosharing.auth_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * DTO для ответа с JWT токенами и основной информацией о пользователе после успешного входа/регистрации.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JwtResponse {

    private String accessToken; // Токен доступа
    private String refreshToken; // Токен обновления
    private final String type = "Bearer"; // Тип токена
    private UUID userId; // Уникальный ID пользователя
    private List<String> roles; // Список ролей пользователя (строковые представления)
    private Long accessTokenExpiresInMs; // Время жизни access токена в мс (от текущего момента)
    private Long refreshTokenExpiresInMs; // Время жизни refresh токена в мс (от текущего момента)
}
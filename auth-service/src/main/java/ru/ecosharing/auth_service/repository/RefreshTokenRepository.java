package ru.ecosharing.auth_service.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying; // Необходимо для операций изменения/удаления
import org.springframework.stereotype.Repository;
import ru.ecosharing.auth_service.model.RefreshToken;
import ru.ecosharing.auth_service.model.UserCredentials; // Импортируем UserCredentials

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Репозиторий для работы с сущностями RefreshToken.
 */
@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    /**
     * Находит refresh токен по его строковому значению.
     * @param token Строка токена.
     * @return Optional с найденным токеном или пустой Optional.
     */
    Optional<RefreshToken> findByToken(String token);

    /**
     * Удаляет все refresh токены, принадлежащие указанному пользователю.
     * @param userCredentials Учетные данные пользователя.
     * @return Количество удаленных токенов.
     */
    @Modifying // Указывает, что метод изменяет данные
    int deleteByUserCredentials(UserCredentials userCredentials);

    /**
     * Удаляет все refresh токены, срок действия которых истек до указанного момента времени.
     * @param now Текущий момент времени.
     */
    @Modifying
    void deleteByExpiryDateBefore(Instant now);

    // Опционально: Найти токен по ID пользователя (может быть полезно)
    // Optional<RefreshToken> findByUserCredentials_UserId(UUID userId);
}

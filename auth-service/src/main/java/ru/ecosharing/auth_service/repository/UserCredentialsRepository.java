package ru.ecosharing.auth_service.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.ecosharing.auth_service.model.UserCredentials;

import java.util.Optional;
import java.util.UUID;

/**
 * Репозиторий для работы с сущностями UserCredentials.
 * Первичный ключ - userId (UUID).
 */
@Repository
public interface UserCredentialsRepository extends JpaRepository<UserCredentials, UUID> { // PK - UUID

    /**
     * Находит учетные данные по ID пользователя.
     * @param userId ID пользователя.
     * @return Optional с найденными учетными данными или пустой Optional.
     */
    Optional<UserCredentials> findByUserId(UUID userId);

    /**
     * Находит учетные данные по ID Telegram.
     * @param telegramId ID пользователя в Telegram.
     * @return Optional с найденными учетными данными или пустой Optional.
     */
    Optional<UserCredentials> findByTelegramId(String telegramId);

    /**
     * Проверяет существование учетных данных по ID Telegram.
     * @param telegramId ID пользователя в Telegram.
     * @return true, если существует, иначе false.
     */
    boolean existsByTelegramId(String telegramId);
}

package ru.ecosharing.user_service.repository;


import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.ecosharing.user_service.model.UserSettings;

import java.util.Optional;
import java.util.UUID;

/**
 * Репозиторий для работы с сущностями UserSettings.
 * Первичный ключ - userId (UUID).
 */
@Repository
public interface UserSettingsRepository extends JpaRepository<UserSettings, UUID> {

    /**
     * Находит настройки по ID пользователя.
     * @param userId ID пользователя.
     * @return Optional с найденными настройками.
     */
    Optional<UserSettings> findByUserId(UUID userId);
}
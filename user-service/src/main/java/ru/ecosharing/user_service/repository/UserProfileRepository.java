package ru.ecosharing.user_service.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.ecosharing.user_service.model.UserProfile;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Репозиторий для работы с сущностями UserProfile.
 * Поддерживает пагинацию и сортировку, а также спецификации для динамических запросов.
 */
@Repository
public interface UserProfileRepository extends JpaRepository<UserProfile, UUID>, JpaSpecificationExecutor<UserProfile> {

    // --- Методы поиска ---
    Optional<UserProfile> findByUserId(UUID userId);
    Optional<UserProfile> findByUsername(String username);
    Optional<UserProfile> findByEmail(String email);
    Optional<UserProfile> findByUsernameOrEmail(String username, String email);

    // --- Методы проверки существования ---
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);

    // --- Примеры других возможных методов ---

    // Поиск активных пользователей с пагинацией
    Page<UserProfile> findByIsActive(boolean isActive, Pageable pageable);

    // Обновление времени последнего входа
    @Modifying(clearAutomatically = true, flushAutomatically = true) // Указываем, что метод изменяет данные
    @Query("UPDATE UserProfile up SET up.lastLoginAt = :loginTime WHERE up.userId = :userId")
    void updateLastLoginTime(@Param("userId") UUID userId, @Param("loginTime") LocalDateTime loginTime);

    // Поиск пользователей по части имени или email (без учета регистра) для админского поиска
    @Query("SELECT up FROM UserProfile up WHERE " +
            "LOWER(up.username) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(up.email) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(up.firstName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(up.lastName) LIKE LOWER(CONCAT('%', :query, '%'))")
    Page<UserProfile> searchByQuery(@Param("query") String query, Pageable pageable);
}
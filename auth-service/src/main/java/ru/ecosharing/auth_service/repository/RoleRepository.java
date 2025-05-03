package ru.ecosharing.auth_service.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.ecosharing.auth_service.model.Role;
import ru.ecosharing.auth_service.model.RoleName;

import java.util.Optional;
import java.util.UUID;

/**
 * Репозиторий для работы с сущностями Role.
 */
@Repository
public interface RoleRepository extends JpaRepository<Role, UUID> {

    /**
     * Находит роль по её имени (enum).
     * @param name Имя роли (RoleName).
     * @return Optional с найденной ролью или пустой Optional.
     */
    Optional<Role> findByName(RoleName name);
}
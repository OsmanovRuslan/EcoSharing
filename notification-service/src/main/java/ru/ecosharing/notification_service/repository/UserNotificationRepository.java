package ru.ecosharing.notification_service.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.ecosharing.notification_service.model.UserNotification;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.UUID;

/**
 * Репозиторий Spring Data JPA для работы с сущностями UserNotification.
 * Предоставляет CRUD операции, пагинацию, сортировку и кастомные методы запросов.
 */
@Repository
public interface UserNotificationRepository extends JpaRepository<UserNotification, UUID> {

    /**
     * Находит страницу уведомлений для указанного пользователя,
     * опционально фильтруя по статусу прочтения.
     * Сортировка должна передаваться через объект Pageable.
     * @param userId ID пользователя.
     * @param isRead Статус прочтения (true - прочитанные, false - непрочитанные).
     * @param pageable Параметры пагинации и сортировки (например, page=0, size=20, sort=createdAt,desc).
     * @return Страница (Page) с уведомлениями UserNotification.
     */
    Page<UserNotification> findByUserIdAndIsReadIs(UUID userId, Boolean isRead, Pageable pageable);

    /**
     * Находит страницу всех уведомлений для указанного пользователя.
     * Сортировка должна передаваться через объект Pageable.
     * @param userId ID пользователя.
     * @param pageable Параметры пагинации и сортировки.
     * @return Страница (Page) с уведомлениями UserNotification.
     */
    Page<UserNotification> findByUserId(UUID userId, Pageable pageable);

    /**
     * Подсчитывает количество непрочитанных уведомлений для пользователя.
     * Используется для отображения счетчика на "колокольчике".
     * @param userId ID пользователя.
     * @return Количество (long) непрочитанных уведомлений.
     */
    long countByUserIdAndIsReadFalse(UUID userId);

    /**
     * Помечает указанные уведомления как прочитанные для конкретного пользователя.
     * Обновляет только те уведомления, которые принадлежат пользователю, есть в списке IDs
     * и еще не были прочитаны. Устанавливает время прочтения.
     *
     * @param userId ID пользователя, чьи уведомления помечаются.
     * @param notificationIds Коллекция (List, Set) ID уведомлений для пометки.
     * @param readAt Момент времени, когда уведомления были прочитаны.
     * @return Количество фактически обновленных (помеченных как прочитанные) записей.
     */
    @Modifying
    @Query("UPDATE UserNotification n SET n.isRead = true, n.readAt = :readAt WHERE n.userId = :userId AND n.id IN :notificationIds AND n.isRead = false")
    int markAsRead(@Param("userId") UUID userId,
                   @Param("notificationIds") Collection<UUID> notificationIds,
                   @Param("readAt") LocalDateTime readAt);

    /**
     * Помечает ВСЕ непрочитанные уведомления пользователя как прочитанные.
     * Устанавливает время прочтения для всех обновленных записей.
     *
     * @param userId ID пользователя.
     * @param readAt Момент времени, когда уведомления были прочитаны.
     * @return Количество фактически обновленных (помеченных как прочитанные) записей.
     */
    @Modifying
    @Query("UPDATE UserNotification n SET n.isRead = true, n.readAt = :readAt WHERE n.userId = :userId AND n.isRead = false")
    int markAllAsRead(@Param("userId") UUID userId, @Param("readAt") LocalDateTime readAt);

}
package ru.ecosharing.listing_service.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.ecosharing.listing_service.model.Category;
import ru.ecosharing.listing_service.model.Listing;
import ru.ecosharing.listing_service.enums.ModerationStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ListingRepository extends JpaRepository<Listing, UUID>, JpaSpecificationExecutor<Listing> {

    // Поиск по ID пользователя (для "моих объявлений")
    Page<Listing> findAllByUserId(UUID userId, Pageable pageable);

    // Поиск по ID пользователя и статусу модерации
    Page<Listing> findAllByUserIdAndModerationStatus(UUID userId, ModerationStatus moderationStatus, Pageable pageable);

    // Поиск конкретного объявления пользователя
    Optional<Listing> findByIdAndUserId(UUID id, UUID userId);

    // Поиск активных объявлений (для общего списка)
    Page<Listing> findAllByModerationStatus(ModerationStatus moderationStatus, Pageable pageable);

    // Получить все объявления в указанной категории со статусом ACTIVE
    List<Listing> findAllByCategoryAndModerationStatus(Category category, ModerationStatus moderationStatus);

    // Метод для инкремента счетчика просмотров
    @Modifying // Указывает, что метод изменяет данные
    @Query("UPDATE Listing l SET l.viewCount = l.viewCount + 1 WHERE l.id = :listingId")
    void incrementViewCount(@Param("listingId") UUID listingId);

    boolean existsByCategory(Category category);

    // Проверка существования активного объявления (полезно для некоторых валидаций)
    boolean existsByIdAndModerationStatus(UUID id, ModerationStatus moderationStatus);

}
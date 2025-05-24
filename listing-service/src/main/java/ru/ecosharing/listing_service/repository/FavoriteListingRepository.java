package ru.ecosharing.listing_service.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.ecosharing.listing_service.model.FavoriteListing;
import ru.ecosharing.listing_service.model.FavoriteListingId;
import ru.ecosharing.listing_service.model.Listing; // Импорт для результата запроса

import java.util.Optional;
import java.util.UUID;

@Repository
public interface FavoriteListingRepository extends JpaRepository<FavoriteListing, FavoriteListingId> {

    // Проверить, добавлено ли объявление в избранное пользователем
    boolean existsById_UserIdAndId_ListingId(UUID userId, UUID listingId);

    // Найти конкретную запись в избранном
    Optional<FavoriteListing> findById_UserIdAndId_ListingId(UUID userId, UUID listingId);

    // Получить все избранные объявления для пользователя (возвращаем сущности Listing)
    // Здесь мы соединяем FavoriteListing с Listing, чтобы получить данные самих объявлений.
    @Query("SELECT l FROM Listing l JOIN FavoriteListing fl ON l.id = fl.id.listingId WHERE fl.id.userId = :userId")
    Page<Listing> findFavoriteListingsByUserId(@Param("userId") UUID userId, Pageable pageable);

    // Подсчитать, сколько раз объявление добавлено в избранное (для статистики)
    long countById_ListingId(UUID listingId);

    // Удалить все записи из избранного для конкретного объявления (например, если объявление удаляется)
    void deleteAllById_ListingId(UUID listingId);

    // Удалить все записи из избранного для конкретного пользователя (например, при удалении пользователя)
    void deleteAllById_UserId(UUID userId);
}
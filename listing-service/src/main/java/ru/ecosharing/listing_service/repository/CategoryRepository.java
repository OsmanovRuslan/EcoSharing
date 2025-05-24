package ru.ecosharing.listing_service.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import ru.ecosharing.listing_service.model.Category;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CategoryRepository extends JpaRepository<Category, UUID> {

    Optional<Category> findByName(String name);

    // Найти все активные категории
    List<Category> findAllByIsActiveTrue();

    // Найти все корневые активные категории (у которых нет родителя)
    List<Category> findAllByParentIsNullAndIsActiveTrue();

    // Найти все дочерние активные категории для данной родительской категории
    List<Category> findAllByParentIdAndIsActiveTrue(UUID parentId);

    // Проверка, есть ли у категории активные дочерние категории (полезно перед деактивацией)
    boolean existsByParentIdAndIsActiveTrue(UUID parentId);

    // Проверка, используется ли категория в каких-либо НЕ УДАЛЕННЫХ объявлениях
    @Query("SELECT CASE WHEN COUNT(l) > 0 THEN TRUE ELSE FALSE END FROM Listing l WHERE l.category.id = :categoryId")
    boolean isCategoryUsedInListings(UUID categoryId);
}
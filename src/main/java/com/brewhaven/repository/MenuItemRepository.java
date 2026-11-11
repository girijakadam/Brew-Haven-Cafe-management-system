package com.brewhaven.repository;

import com.brewhaven.entity.MenuItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface MenuItemRepository extends JpaRepository<MenuItem, Long> {

    Optional<MenuItem> findByName(String name);

    boolean existsByName(String name);

    List<MenuItem> findByCategoryId(Long categoryId);

    List<MenuItem> findByIsAvailableTrue();

    List<MenuItem> findByCategoryIdAndIsAvailableTrue(Long categoryId);

    List<MenuItem> findByIsVegetarianAndIsAvailableTrue(Boolean isVegetarian);

    List<MenuItem> findByCategoryIdAndIsVegetarianAndIsAvailableTrue(Long categoryId, Boolean isVegetarian);

    @Query("SELECT m FROM MenuItem m WHERE m.isAvailable = true AND " +
           "(LOWER(m.name) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(m.description) LIKE LOWER(CONCAT('%', :query, '%')))")
    List<MenuItem> searchAvailable(@Param("query") String query);

    @Query("SELECT m FROM MenuItem m WHERE m.isAvailable = true " +
           "AND (:categoryId IS NULL OR m.category.id = :categoryId) " +
           "AND (:isVegetarian IS NULL OR m.isVegetarian = :isVegetarian) " +
           "AND (:maxPrice IS NULL OR m.price <= :maxPrice) " +
           "AND (:query IS NULL OR LOWER(m.name) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "     OR LOWER(m.description) LIKE LOWER(CONCAT('%', :query, '%')))")
    List<MenuItem> filterMenu(
            @Param("categoryId") Long categoryId,
            @Param("isVegetarian") Boolean isVegetarian,
            @Param("maxPrice") BigDecimal maxPrice,
            @Param("query") String query
    );
}

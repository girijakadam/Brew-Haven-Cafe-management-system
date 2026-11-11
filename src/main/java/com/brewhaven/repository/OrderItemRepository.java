package com.brewhaven.repository;

import com.brewhaven.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    List<OrderItem> findByMenuItemId(Long menuItemId);

    @Query("SELECT oi.itemName, SUM(oi.quantity), SUM(oi.subtotal) " +
           "FROM OrderItem oi JOIN oi.order o WHERE o.status = 'COMPLETED' " +
           "GROUP BY oi.itemName ORDER BY SUM(oi.quantity) DESC")
    List<Object[]> findTopSellingItems();
}

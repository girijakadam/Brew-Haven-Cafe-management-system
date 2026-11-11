package com.brewhaven.repository;

import com.brewhaven.entity.Order;
import com.brewhaven.entity.OrderStatus;
import com.brewhaven.entity.OrderTiming;
import com.brewhaven.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByOrderNumber(String orderNumber);

    boolean existsByOrderNumber(String orderNumber);

    List<Order> findByUserOrderByCreatedAtDesc(User user);

    List<Order> findAllByOrderByCreatedAtDesc();

    List<Order> findByStatusOrderByCreatedAtDesc(OrderStatus status);

    List<Order> findByOrderTimingOrderByCreatedAtDesc(OrderTiming orderTiming);

    @Query("SELECT o FROM Order o WHERE o.orderTiming = 'PRE_ORDER' AND o.status IN ('CONFIRMED', 'PREPARING') ORDER BY o.expectedArrivalTime ASC")
    List<Order> findUpcomingPreOrders();

    @Query("SELECT o FROM Order o WHERE o.status IN ('PENDING', 'CONFIRMED', 'PREPARING', 'READY') ORDER BY o.createdAt DESC")
    List<Order> findActiveOrders();

    long countByStatus(OrderStatus status);

    @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o WHERE o.status = 'COMPLETED'")
    BigDecimal calculateTotalRevenue();

    @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o WHERE o.status = 'COMPLETED' AND o.createdAt >= :since")
    BigDecimal calculateRevenueSince(@Param("since") LocalDateTime since);

    long countByCreatedAtAfter(LocalDateTime since);
}

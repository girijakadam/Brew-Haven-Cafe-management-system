package com.brewhaven.repository;

import com.brewhaven.entity.Review;
import com.brewhaven.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {
    List<Review> findAllByOrderByCreatedAtDesc();
    List<Review> findByUserOrderByCreatedAtDesc(User user);
    Optional<Review> findByOrderId(Long orderId);

    @Query("SELECT COALESCE(AVG(r.rating), 0.0) FROM Review r")
    Double getAverageRating();

    @Query("SELECT r.rating, COUNT(r) FROM Review r GROUP BY r.rating ORDER BY r.rating DESC")
    List<Object[]> getRatingDistribution();
}

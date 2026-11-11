package com.brewhaven.service;

import com.brewhaven.dto.ReviewRequest;
import com.brewhaven.entity.Order;
import com.brewhaven.entity.OrderStatus;
import com.brewhaven.entity.Review;
import com.brewhaven.entity.User;
import com.brewhaven.exception.BadRequestException;
import com.brewhaven.exception.ResourceNotFoundException;
import com.brewhaven.repository.OrderRepository;
import com.brewhaven.repository.ReviewRepository;
import com.brewhaven.repository.UserRepository;
import com.brewhaven.security.UserPrincipal;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;

    public ReviewService(ReviewRepository reviewRepository,
                         OrderRepository orderRepository,
                         UserRepository userRepository) {
        this.reviewRepository = reviewRepository;
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public Review submitReview(UserPrincipal principal, ReviewRequest request) {
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", principal.getId()));

        Order order = orderRepository.findById(request.getOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", request.getOrderId()));

        // Check if current user is the owner of order
        if (order.getUser() == null || !order.getUser().getId().equals(user.getId())) {
            throw new AccessDeniedException("You can only review your own orders");
        }

        if (order.getStatus() != OrderStatus.COMPLETED) {
            throw new BadRequestException("Reviews can only be submitted for completed orders");
        }

        if (reviewRepository.findByOrderId(order.getId()).isPresent()) {
            throw new BadRequestException("Review already submitted for this order");
        }

        Review review = new Review();
        review.setUser(user);
        review.setOrder(order);
        review.setRating(request.getRating());
        review.setComment(request.getComment());

        return reviewRepository.save(review);
    }

    @Transactional(readOnly = true)
    public List<Review> getAllReviews() {
        return reviewRepository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public Double getAverageRating() {
        return reviewRepository.getAverageRating();
    }

    @Transactional(readOnly = true)
    public Map<Integer, Long> getRatingDistribution() {
        Map<Integer, Long> distribution = new HashMap<>();
        for (int i = 1; i <= 5; i++) {
            distribution.put(i, 0L);
        }
        List<Object[]> rows = reviewRepository.getRatingDistribution();
        for (Object[] row : rows) {
            if (row[0] != null && row[1] != null) {
                Integer rating = ((Number) row[0]).intValue();
                Long count = ((Number) row[1]).longValue();
                distribution.put(rating, count);
            }
        }
        return distribution;
    }
}

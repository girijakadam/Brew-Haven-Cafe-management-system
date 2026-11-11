package com.brewhaven.controller;

import com.brewhaven.dto.ApiResponse;
import com.brewhaven.dto.ReviewRequest;
import com.brewhaven.entity.Review;
import com.brewhaven.security.UserPrincipal;
import com.brewhaven.service.ReviewService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reviews")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse> submitReview(@AuthenticationPrincipal UserPrincipal principal,
                                                    @Valid @RequestBody ReviewRequest request) {
        Review review = reviewService.submitReview(principal, request);
        return new ResponseEntity<>(ApiResponse.ok("Review submitted successfully", review), HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<List<Review>> getAllReviews() {
        return ResponseEntity.ok(reviewService.getAllReviews());
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getReviewStats() {
        return ResponseEntity.ok(Map.of(
                "averageRating", reviewService.getAverageRating(),
                "distribution", reviewService.getRatingDistribution(),
                "totalReviews", reviewService.getAllReviews().size()
        ));
    }
}

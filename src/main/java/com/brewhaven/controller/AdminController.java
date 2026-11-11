package com.brewhaven.controller;

import com.brewhaven.dto.AdminResetPasswordRequest;
import com.brewhaven.dto.CreateUserAdminRequest;
import com.brewhaven.dto.DashboardStatsDto;
import com.brewhaven.dto.OrderResponse;
import com.brewhaven.entity.OrderStatus;
import com.brewhaven.entity.Role;
import com.brewhaven.entity.User;
import com.brewhaven.exception.BadRequestException;
import com.brewhaven.exception.ResourceNotFoundException;
import com.brewhaven.repository.OrderItemRepository;
import com.brewhaven.repository.OrderRepository;
import com.brewhaven.repository.ReviewRepository;
import com.brewhaven.repository.UserRepository;
import com.brewhaven.security.UserPrincipal;
import com.brewhaven.service.OrderService;
import com.brewhaven.service.ReviewService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ReviewRepository reviewRepository;
    private final UserRepository userRepository;
    private final OrderService orderService;
    private final ReviewService reviewService;
    private final PasswordEncoder passwordEncoder;

    public AdminController(OrderRepository orderRepository,
                           OrderItemRepository orderItemRepository,
                           ReviewRepository reviewRepository,
                           UserRepository userRepository,
                           OrderService orderService,
                           ReviewService reviewService,
                           PasswordEncoder passwordEncoder) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.reviewRepository = reviewRepository;
        this.userRepository = userRepository;
        this.orderService = orderService;
        this.reviewService = reviewService;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping("/stats")
    public ResponseEntity<DashboardStatsDto> getDashboardStats() {
        DashboardStatsDto stats = new DashboardStatsDto();

        LocalDateTime startOfToday = LocalDate.now().atStartOfDay();

        stats.setTotalRevenue(orderRepository.calculateTotalRevenue());
        stats.setTodayRevenue(orderRepository.calculateRevenueSince(startOfToday));

        stats.setTotalOrders(orderRepository.count());
        stats.setTodayOrders(orderRepository.countByCreatedAtAfter(startOfToday));

        stats.setActiveOrders(orderRepository.findActiveOrders().size());
        stats.setPendingOrders(orderRepository.countByStatus(OrderStatus.PENDING));
        stats.setPreparingOrders(orderRepository.countByStatus(OrderStatus.PREPARING));
        stats.setReadyOrders(orderRepository.countByStatus(OrderStatus.READY));
        stats.setCompletedOrders(orderRepository.countByStatus(OrderStatus.COMPLETED));

        stats.setAverageRating(reviewRepository.getAverageRating());
        stats.setTotalReviews(reviewRepository.count());
        stats.setRatingDistribution(reviewService.getRatingDistribution());

        List<Object[]> topItemsData = orderItemRepository.findTopSellingItems();
        List<Map<String, Object>> topSellingList = new ArrayList<>();
        for (Object[] row : topItemsData) {
            Map<String, Object> map = new HashMap<>();
            map.put("itemName", row[0]);
            map.put("totalQuantity", row[1]);
            map.put("totalSales", row[2]);
            topSellingList.add(map);
        }
        stats.setTopSellingItems(topSellingList);

        return ResponseEntity.ok(stats);
    }

    @GetMapping("/orders")
    public ResponseEntity<List<OrderResponse>> getAllOrders() {
        return ResponseEntity.ok(orderService.getAllOrders());
    }

    @GetMapping("/users")
    public ResponseEntity<List<Map<String, Object>>> getAllUsers() {
        List<User> users = userRepository.findAll();
        List<Map<String, Object>> userDtos = users.stream().map(u -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", u.getId());
            map.put("name", u.getName());
            map.put("username", u.getUsername() != null ? u.getUsername() : "");
            map.put("email", u.getEmail());
            map.put("phone", u.getPhone() != null ? u.getPhone() : "");
            map.put("role", u.getRole().name());
            map.put("createdAt", u.getCreatedAt());
            map.put("mustChangePassword", Boolean.TRUE.equals(u.getMustChangePassword()));
            map.put("passwordResetBy", u.getPasswordResetBy() != null ? u.getPasswordResetBy() : "");
            map.put("passwordResetAt", u.getPasswordResetAt());
            return map;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(userDtos);
    }

    @PostMapping("/users/{id}/reset-password")
    public ResponseEntity<Map<String, Object>> resetUserPassword(
            @AuthenticationPrincipal UserPrincipal adminPrincipal,
            @PathVariable Long id,
            @Valid @RequestBody AdminResetPasswordRequest request) {

        if (request.getNewPassword() == null || request.getNewPassword().length() < 6) {
            throw new BadRequestException("New temporary password must be at least 6 characters");
        }
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new BadRequestException("New password and confirm password do not match");
        }

        User targetUser = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));

        targetUser.setPassword(passwordEncoder.encode(request.getNewPassword()));
        targetUser.setMustChangePassword(true);
        targetUser.setPasswordResetBy(adminPrincipal != null ? adminPrincipal.getUsername() : "ADMIN");
        targetUser.setPasswordResetAt(LocalDateTime.now());
        targetUser.setPasswordChangedAt(LocalDateTime.now());

        User saved = userRepository.save(targetUser);

        Map<String, Object> resp = new HashMap<>();
        resp.put("success", true);
        resp.put("message", "Password successfully reset for user: " + saved.getName());
        resp.put("userId", saved.getId());
        resp.put("userName", saved.getName());
        resp.put("mustChangePassword", true);
        resp.put("passwordResetBy", saved.getPasswordResetBy());
        resp.put("passwordResetAt", saved.getPasswordResetAt());

        return ResponseEntity.ok(resp);
    }

    @PostMapping("/users")
    public ResponseEntity<Map<String, Object>> createUser(@Valid @RequestBody CreateUserAdminRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("Email is already registered: " + request.getEmail());
        }

        User user = new User();
        user.setName(request.getName());
        user.setEmail(request.getEmail().toLowerCase().trim());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setPhone(request.getPhone());
        user.setRole(request.getRole());

        User saved = userRepository.save(user);

        Map<String, Object> map = new HashMap<>();
        map.put("id", saved.getId());
        map.put("name", saved.getName());
        map.put("email", saved.getEmail());
        map.put("phone", saved.getPhone() != null ? saved.getPhone() : "");
        map.put("role", saved.getRole().name());
        map.put("createdAt", saved.getCreatedAt());

        return new ResponseEntity<>(map, HttpStatus.CREATED);
    }

    @PatchMapping("/users/{id}/role")
    public ResponseEntity<Map<String, Object>> updateUserRole(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String roleStr = body.get("role");
        if (roleStr == null || roleStr.isBlank()) {
            throw new BadRequestException("Role is required");
        }

        Role newRole;
        try {
            newRole = Role.valueOf(roleStr);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid role: " + roleStr);
        }

        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
        user.setRole(newRole);
        User saved = userRepository.save(user);

        Map<String, Object> map = new HashMap<>();
        map.put("id", saved.getId());
        map.put("name", saved.getName());
        map.put("email", saved.getEmail());
        map.put("phone", saved.getPhone() != null ? saved.getPhone() : "");
        map.put("role", saved.getRole().name());
        map.put("createdAt", saved.getCreatedAt());

        return ResponseEntity.ok(map);
    }
}

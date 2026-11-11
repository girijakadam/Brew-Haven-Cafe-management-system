package com.brewhaven.controller;

import com.brewhaven.dto.OrderCreateRequest;
import com.brewhaven.dto.OrderResponse;
import com.brewhaven.dto.OrderStatusUpdateRequest;
import com.brewhaven.security.UserPrincipal;
import com.brewhaven.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<OrderResponse> createOrder(@AuthenticationPrincipal UserPrincipal principal,
                                                     @Valid @RequestBody OrderCreateRequest request) {
        OrderResponse response = orderService.createOrder(principal, request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping("/my-orders")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<List<OrderResponse>> getMyOrders(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(orderService.getOrdersByUser(principal));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<OrderResponse> getOrderById(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        return ResponseEntity.ok(orderService.getOrderById(principal, id));
    }

    @GetMapping("/number/{orderNumber}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<OrderResponse> getOrderByNumber(@AuthenticationPrincipal UserPrincipal principal, @PathVariable String orderNumber) {
        return ResponseEntity.ok(orderService.getOrderByOrderNumber(principal, orderNumber));
    }

    @GetMapping("/active")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    public ResponseEntity<List<OrderResponse>> getActiveOrders() {
        return ResponseEntity.ok(orderService.getActiveOrders());
    }

    @GetMapping("/upcoming-preorders")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    public ResponseEntity<List<OrderResponse>> getUpcomingPreOrders() {
        return ResponseEntity.ok(orderService.getUpcomingPreOrders());
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    public ResponseEntity<OrderResponse> updateOrderStatus(@PathVariable Long id,
                                                           @Valid @RequestBody OrderStatusUpdateRequest request) {
        OrderResponse updated = orderService.updateOrderStatus(id, request.getStatus());
        return ResponseEntity.ok(updated);
    }
}

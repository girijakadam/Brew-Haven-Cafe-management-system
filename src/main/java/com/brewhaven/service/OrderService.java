package com.brewhaven.service;

import com.brewhaven.dto.*;
import com.brewhaven.entity.*;
import com.brewhaven.exception.BadRequestException;
import com.brewhaven.exception.ResourceNotFoundException;
import com.brewhaven.repository.*;
import com.brewhaven.security.UserPrincipal;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
public class OrderService {

    private static final BigDecimal TAX_RATE = new BigDecimal("0.05"); // 5% GST
    private static final AtomicInteger ORDER_SEQUENCE = new AtomicInteger(100);

    private final OrderRepository orderRepository;
    private final MenuItemRepository menuItemRepository;
    private final CafeTableRepository cafeTableRepository;
    private final UserRepository userRepository;
    private final PaymentService paymentService;
    private final ReviewRepository reviewRepository;

    public OrderService(OrderRepository orderRepository,
                        MenuItemRepository menuItemRepository,
                        CafeTableRepository cafeTableRepository,
                        UserRepository userRepository,
                        PaymentService paymentService,
                        ReviewRepository reviewRepository) {
        this.orderRepository = orderRepository;
        this.menuItemRepository = menuItemRepository;
        this.cafeTableRepository = cafeTableRepository;
        this.userRepository = userRepository;
        this.paymentService = paymentService;
        this.reviewRepository = reviewRepository;
    }

    @Transactional
    public OrderResponse createOrder(UserPrincipal principal, OrderCreateRequest request) {
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", principal.getId()));

        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new BadRequestException("Order must have at least one item");
        }

        Order order = new Order();
        order.setUser(user);
        order.setOrderTiming(request.getOrderTiming());
        order.setFulfilmentType(request.getFulfilmentType());
        order.setNotes(request.getNotes());

        // Validate timing
        if (request.getOrderTiming() == OrderTiming.PRE_ORDER) {
            if (request.getExpectedArrivalTime() == null) {
                throw new BadRequestException("Expected arrival time is required for Pre-Orders");
            }
            if (request.getExpectedArrivalTime().isBefore(LocalDateTime.now().plusMinutes(5))) {
                throw new BadRequestException("Pre-order arrival time must be at least 5 minutes in the future");
            }
            order.setExpectedArrivalTime(request.getExpectedArrivalTime());
        } else {
            order.setExpectedArrivalTime(LocalDateTime.now().plusMinutes(20)); // estimated prep time
        }

        // Table allocation for Immediate Dine-In only (QR scan or seated order)
        // Note: Advance table reservation is explicitly excluded from scope.
        if (request.getOrderTiming() == OrderTiming.IMMEDIATE && request.getFulfilmentType() == FulfilmentType.DINE_IN) {
            if (request.getTableNumber() != null) {
                CafeTable table = cafeTableRepository.findByTableNumber(request.getTableNumber())
                        .orElseThrow(() -> new ResourceNotFoundException("Table", "tableNumber", request.getTableNumber()));
                order.setCafeTable(table);
                order.setTableNumber(table.getTableNumber());
                table.setStatus("OCCUPIED");
                cafeTableRepository.save(table);
            }
        } else {
            // For Pre-Orders or Takeaway, table is never reserved in advance
            order.setCafeTable(null);
            order.setTableNumber(null);
        }

        // Generate human-friendly order number: BH-YYYYMMDD-SEQ
        String orderNumber = generateOrderNumber();
        order.setOrderNumber(orderNumber);

        // Process items & subtotal
        BigDecimal subtotal = BigDecimal.ZERO;
        List<OrderItem> orderItems = new ArrayList<>();

        for (OrderItemRequest itemReq : request.getItems()) {
            MenuItem menuItem = menuItemRepository.findById(itemReq.getMenuItemId())
                    .orElseThrow(() -> new ResourceNotFoundException("MenuItem", "id", itemReq.getMenuItemId()));

            if (!Boolean.TRUE.equals(menuItem.getIsAvailable())) {
                throw new BadRequestException("Item '" + menuItem.getName() + "' is currently unavailable");
            }

            BigDecimal itemPrice = menuItem.getPrice();
            BigDecimal itemSubtotal = itemPrice.multiply(BigDecimal.valueOf(itemReq.getQuantity()));
            subtotal = subtotal.add(itemSubtotal);

            OrderItem orderItem = new OrderItem();
            orderItem.setOrder(order);
            orderItem.setMenuItem(menuItem);
            orderItem.setItemName(menuItem.getName());
            orderItem.setItemPrice(itemPrice);
            orderItem.setQuantity(itemReq.getQuantity());
            orderItem.setSubtotal(itemSubtotal);

            orderItems.add(orderItem);
        }

        BigDecimal tax = subtotal.multiply(TAX_RATE).setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalAmount = subtotal.add(tax).setScale(2, RoundingMode.HALF_UP);

        order.setSubtotal(subtotal);
        order.setTax(tax);
        order.setTotalAmount(totalAmount);
        order.setStatus(OrderStatus.CONFIRMED);

        for (OrderItem oi : orderItems) {
            order.addItem(oi);
        }

        Order savedOrder = orderRepository.save(order);

        // Process payment
        Payment payment = paymentService.initializePayment(savedOrder, request.getPaymentMethod());
        savedOrder.setPayment(payment);

        return toOrderResponse(savedOrder);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrderById(UserPrincipal principal, Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", id));
        validateOrderAccess(principal, order);
        return toOrderResponse(order);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrderByOrderNumber(UserPrincipal principal, String orderNumber) {
        Order order = orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "orderNumber", orderNumber));
        validateOrderAccess(principal, order);
        return toOrderResponse(order);
    }

    private void validateOrderAccess(UserPrincipal principal, Order order) {
        if (principal == null) {
            throw new AccessDeniedException("Not authenticated");
        }
        boolean isAdmin = principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        boolean isStaff = principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_STAFF"));
        if (isAdmin || isStaff) {
            return;
        }
        if (order.getUser() == null || !order.getUser().getId().equals(principal.getId())) {
            throw new AccessDeniedException("Access denied: You cannot view orders placed by other customers");
        }
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getOrdersByUser(UserPrincipal principal) {
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", principal.getId()));
        return orderRepository.findByUserOrderByCreatedAtDesc(user)
                .stream()
                .map(this::toOrderResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getAllOrders() {
        return orderRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(this::toOrderResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getActiveOrders() {
        return orderRepository.findActiveOrders()
                .stream()
                .map(this::toOrderResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getUpcomingPreOrders() {
        return orderRepository.findUpcomingPreOrders()
                .stream()
                .map(this::toOrderResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public OrderResponse updateOrderStatus(Long orderId, OrderStatus newStatus) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));

        order.setStatus(newStatus);
        order.setUpdatedAt(LocalDateTime.now());

        // When order completes or is cancelled, release table
        if ((newStatus == OrderStatus.COMPLETED || newStatus == OrderStatus.CANCELLED) && order.getCafeTable() != null) {
            CafeTable table = order.getCafeTable();
            table.setStatus("AVAILABLE");
            cafeTableRepository.save(table);
        }

        // When completed, if cash payment was pending, mark success
        if (newStatus == OrderStatus.COMPLETED && order.getPayment() != null) {
            order.getPayment().setPaymentStatus(PaymentStatus.SUCCESS);
        }

        Order updatedOrder = orderRepository.save(order);
        return toOrderResponse(updatedOrder);
    }

    public OrderResponse toOrderResponse(Order order) {
        OrderResponse res = new OrderResponse();
        res.setId(order.getId());
        res.setOrderNumber(order.getOrderNumber());
        if (order.getUser() != null) {
            res.setUserId(order.getUser().getId());
            res.setCustomerName(order.getUser().getName());
            res.setCustomerEmail(order.getUser().getEmail());
            res.setCustomerPhone(order.getUser().getPhone());
        }
        res.setOrderTiming(order.getOrderTiming());
        res.setFulfilmentType(order.getFulfilmentType());
        res.setTableNumber(order.getTableNumber());
        res.setExpectedArrivalTime(order.getExpectedArrivalTime());
        res.setStatus(order.getStatus());
        res.setSubtotal(order.getSubtotal());
        res.setTax(order.getTax());
        res.setTotalAmount(order.getTotalAmount());
        res.setNotes(order.getNotes());
        res.setCreatedAt(order.getCreatedAt());
        res.setUpdatedAt(order.getUpdatedAt());

        if (order.getPayment() != null) {
            res.setPaymentMethod(order.getPayment().getPaymentMethod());
            res.setPaymentStatus(order.getPayment().getPaymentStatus());
            res.setTransactionReference(order.getPayment().getTransactionReference());
        }

        if (order.getItems() != null) {
            List<OrderItemResponse> itemResponses = order.getItems().stream().map(item -> {
                Long menuId = item.getMenuItem() != null ? item.getMenuItem().getId() : null;
                return new OrderItemResponse(item.getId(), menuId, item.getItemName(), item.getItemPrice(), item.getQuantity(), item.getSubtotal());
            }).collect(Collectors.toList());
            res.setItems(itemResponses);
        }

        res.setHasReview(reviewRepository.findByOrderId(order.getId()).isPresent());

        return res;
    }

    private String generateOrderNumber() {
        String datePart = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyMMdd"));
        for (int i = 0; i < 1000; i++) {
            String candidate = "BH-" + datePart + "-" + ORDER_SEQUENCE.incrementAndGet();
            if (!orderRepository.existsByOrderNumber(candidate)) {
                return candidate;
            }
        }
        return "BH-" + datePart + "-" + java.util.UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}

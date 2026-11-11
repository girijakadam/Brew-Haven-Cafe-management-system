package com.brewhaven;

import com.brewhaven.dto.*;
import com.brewhaven.entity.*;
import com.brewhaven.repository.MenuItemRepository;
import com.brewhaven.repository.OrderRepository;
import com.brewhaven.repository.UserRepository;
import com.brewhaven.security.UserPrincipal;
import com.brewhaven.service.AuthService;
import com.brewhaven.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class OrderHistoryAndRoleIsolationTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MenuItemRepository menuItemRepository;

    @Autowired
    private OrderRepository orderRepository;

    private UserPrincipal customerAPrincipal;
    private UserPrincipal customerBPrincipal;
    private MenuItem sampleItem;

    @BeforeEach
    void setUp() {
        // Register Customer A
        RegisterRequest reqA = new RegisterRequest();
        reqA.setName("Customer Alpha");
        reqA.setPhone("9900000001");
        reqA.setPassword("alphaPass123");
        AuthResponse respA = authService.register(reqA);
        User userA = userRepository.findById(respA.getId()).orElseThrow();
        customerAPrincipal = UserPrincipal.create(userA);

        // Register Customer B
        RegisterRequest reqB = new RegisterRequest();
        reqB.setName("Customer Beta");
        reqB.setPhone("9900000002");
        reqB.setPassword("betaPass123");
        AuthResponse respB = authService.register(reqB);
        User userB = userRepository.findById(respB.getId()).orElseThrow();
        customerBPrincipal = UserPrincipal.create(userB);

        // Get an available menu item
        sampleItem = menuItemRepository.findAll().stream()
                .filter(MenuItem::getIsAvailable)
                .findFirst()
                .orElseThrow();
    }

    @Test
    @DisplayName("1. Order is associated with authenticated customer database ID")
    void testOrderAssociationWithCustomerId() {
        OrderCreateRequest orderReq = new OrderCreateRequest();
        orderReq.setOrderTiming(OrderTiming.IMMEDIATE);
        orderReq.setFulfilmentType(FulfilmentType.TAKEAWAY);
        orderReq.setPaymentMethod(PaymentMethod.UPI);
        OrderItemRequest itemReq = new OrderItemRequest();
        itemReq.setMenuItemId(sampleItem.getId());
        itemReq.setQuantity(2);
        orderReq.setItems(Collections.singletonList(itemReq));

        OrderResponse orderResp = orderService.createOrder(customerAPrincipal, orderReq);

        assertNotNull(orderResp);
        assertNotNull(orderResp.getOrderNumber());
        assertEquals(FulfilmentType.TAKEAWAY, orderResp.getFulfilmentType());

        // Verify order in database is associated with customer A
        Order order = orderRepository.findById(orderResp.getId()).orElseThrow();
        assertNotNull(order.getUser());
        assertEquals(customerAPrincipal.getId(), order.getUser().getId());
        assertEquals("Customer Alpha", order.getUser().getName());
    }

    @Test
    @DisplayName("2. Order history isolation: Customer sees only their own orders")
    void testOrderHistoryIsolation() {
        // Customer A places an order
        OrderCreateRequest orderReqA = new OrderCreateRequest();
        orderReqA.setOrderTiming(OrderTiming.IMMEDIATE);
        orderReqA.setFulfilmentType(FulfilmentType.TAKEAWAY);
        orderReqA.setPaymentMethod(PaymentMethod.UPI);
        OrderItemRequest itemReqA = new OrderItemRequest();
        itemReqA.setMenuItemId(sampleItem.getId());
        itemReqA.setQuantity(1);
        orderReqA.setItems(Collections.singletonList(itemReqA));
        orderService.createOrder(customerAPrincipal, orderReqA);

        // Customer B places an order
        OrderCreateRequest orderReqB = new OrderCreateRequest();
        orderReqB.setOrderTiming(OrderTiming.IMMEDIATE);
        orderReqB.setFulfilmentType(FulfilmentType.TAKEAWAY);
        orderReqB.setPaymentMethod(PaymentMethod.CASH);
        OrderItemRequest itemReqB = new OrderItemRequest();
        itemReqB.setMenuItemId(sampleItem.getId());
        itemReqB.setQuantity(3);
        orderReqB.setItems(Collections.singletonList(itemReqB));
        orderService.createOrder(customerBPrincipal, orderReqB);

        // Retrieve orders for Customer A
        List<OrderResponse> customerAOrders = orderService.getOrdersByUser(customerAPrincipal);
        assertEquals(1, customerAOrders.size());
        assertEquals(PaymentMethod.UPI, customerAOrders.get(0).getPaymentMethod());

        // Retrieve orders for Customer B
        List<OrderResponse> customerBOrders = orderService.getOrdersByUser(customerBPrincipal);
        assertEquals(1, customerBOrders.size());
        assertEquals(PaymentMethod.CASH, customerBOrders.get(0).getPaymentMethod());
    }

    @Test
    @DisplayName("3. Object ownership: Customer A cannot view Customer B's order by ID")
    void testObjectOwnershipEnforcement() {
        // Customer B places an order
        OrderCreateRequest orderReqB = new OrderCreateRequest();
        orderReqB.setOrderTiming(OrderTiming.IMMEDIATE);
        orderReqB.setFulfilmentType(FulfilmentType.TAKEAWAY);
        orderReqB.setPaymentMethod(PaymentMethod.CARD);
        OrderItemRequest itemReqB = new OrderItemRequest();
        itemReqB.setMenuItemId(sampleItem.getId());
        itemReqB.setQuantity(1);
        orderReqB.setItems(Collections.singletonList(itemReqB));
        OrderResponse orderB = orderService.createOrder(customerBPrincipal, orderReqB);

        // Customer A attempts to access Customer B's order by ID
        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () ->
                orderService.getOrderById(customerAPrincipal, orderB.getId())
        );
        assertTrue(ex.getMessage().contains("Access denied: You cannot view orders placed by other customers"));

        // Customer A attempts to access Customer B's order by Order Number
        AccessDeniedException exNo = assertThrows(AccessDeniedException.class, () ->
                orderService.getOrderByOrderNumber(customerAPrincipal, orderB.getOrderNumber())
        );
        assertTrue(exNo.getMessage().contains("Access denied: You cannot view orders placed by other customers"));
    }
}

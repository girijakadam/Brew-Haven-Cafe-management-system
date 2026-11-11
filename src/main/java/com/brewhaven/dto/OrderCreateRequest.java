package com.brewhaven.dto;

import com.brewhaven.entity.FulfilmentType;
import com.brewhaven.entity.OrderTiming;
import com.brewhaven.entity.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;

public class OrderCreateRequest {

    @NotEmpty(message = "Order must contain at least one item")
    @Valid
    private List<OrderItemRequest> items;

    @NotNull(message = "Order timing is required (IMMEDIATE or PRE_ORDER)")
    private OrderTiming orderTiming;

    @NotNull(message = "Fulfilment type is required (DINE_IN or TAKEAWAY)")
    private FulfilmentType fulfilmentType;

    private Integer tableNumber;

    private LocalDateTime expectedArrivalTime;

    @NotNull(message = "Payment method is required")
    private PaymentMethod paymentMethod;

    private String notes;

    public OrderCreateRequest() {
    }

    public List<OrderItemRequest> getItems() {
        return items;
    }

    public void setItems(List<OrderItemRequest> items) {
        this.items = items;
    }

    public OrderTiming getOrderTiming() {
        return orderTiming;
    }

    public void setOrderTiming(OrderTiming orderTiming) {
        this.orderTiming = orderTiming;
    }

    public FulfilmentType getFulfilmentType() {
        return fulfilmentType;
    }

    public void setFulfilmentType(FulfilmentType fulfilmentType) {
        this.fulfilmentType = fulfilmentType;
    }

    public Integer getTableNumber() {
        return tableNumber;
    }

    public void setTableNumber(Integer tableNumber) {
        this.tableNumber = tableNumber;
    }

    public LocalDateTime getExpectedArrivalTime() {
        return expectedArrivalTime;
    }

    public void setExpectedArrivalTime(LocalDateTime expectedArrivalTime) {
        this.expectedArrivalTime = expectedArrivalTime;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(PaymentMethod paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}

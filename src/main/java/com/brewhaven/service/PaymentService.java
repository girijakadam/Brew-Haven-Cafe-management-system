package com.brewhaven.service;

import com.brewhaven.entity.*;
import com.brewhaven.exception.ResourceNotFoundException;
import com.brewhaven.repository.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;

    public PaymentService(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    @Transactional
    public Payment initializePayment(Order order, PaymentMethod paymentMethod) {
        String txRef = "TXN-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        // For UPI and CARD in this system, simulate instant successful payment;
        // for CASH at counter, mark as PENDING until staff confirms it.
        PaymentStatus initialStatus = (paymentMethod == PaymentMethod.CASH) ? PaymentStatus.PENDING : PaymentStatus.SUCCESS;

        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setAmount(order.getTotalAmount());
        payment.setPaymentMethod(paymentMethod);
        payment.setPaymentStatus(initialStatus);
        payment.setTransactionReference(txRef);
        payment.setPaymentDate(LocalDateTime.now());
        payment.setNotes("Payment via " + paymentMethod.name());

        return paymentRepository.save(payment);
    }

    @Transactional
    public Payment updatePaymentStatus(Long paymentId, PaymentStatus status) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", "id", paymentId));
        payment.setPaymentStatus(status);
        return paymentRepository.save(payment);
    }
}

package com.brewhaven.repository;

import com.brewhaven.entity.Payment;
import com.brewhaven.entity.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByTransactionReference(String transactionReference);
    Optional<Payment> findByOrderId(Long orderId);
    List<Payment> findByPaymentStatusOrderByPaymentDateDesc(PaymentStatus paymentStatus);
}

package com.edu.subscription_service.repository;

import com.edu.subscription_service.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    
    List<Payment> findByUserId(UUID userId);
    
    Optional<Payment> findByStripePaymentIntentId(String stripePaymentIntentId);
    
    @Query("SELECT p FROM Payment p WHERE p.userId = :userId AND p.status = :status")
    List<Payment> findByUserIdAndStatus(@Param("userId") UUID userId, @Param("status") Payment.PaymentStatus status);
    
    @Query("SELECT p FROM Payment p WHERE p.subscription.id = :subscriptionId")
    List<Payment> findBySubscriptionId(@Param("subscriptionId") UUID subscriptionId);
}

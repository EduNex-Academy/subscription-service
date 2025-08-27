package com.edu.subscription_service.repository;

import com.edu.subscription_service.entity.UserSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserSubscriptionRepository extends JpaRepository<UserSubscription, UUID> {
    
    List<UserSubscription> findByUserId(UUID userId);
    
    @Query("SELECT us FROM UserSubscription us WHERE us.userId = :userId AND us.status = 'ACTIVE'")
    Optional<UserSubscription> findActiveByUserId(@Param("userId") UUID userId);
    
    @Query("SELECT us FROM UserSubscription us WHERE us.userId = :userId AND us.status = 'PENDING'")
    Optional<UserSubscription> findPendingByUserId(@Param("userId") UUID userId);
    
    Optional<UserSubscription> findByStripeSubscriptionId(String stripeSubscriptionId);
    
    @Query("SELECT us FROM UserSubscription us WHERE us.endDate < :currentTime AND us.status = 'ACTIVE'")
    List<UserSubscription> findExpiredSubscriptions(@Param("currentTime") LocalDateTime currentTime);
    
    @Query("SELECT us FROM UserSubscription us WHERE us.status = :status")
    List<UserSubscription> findByStatus(@Param("status") UserSubscription.SubscriptionStatus status);
    
    @Query("SELECT us FROM UserSubscription us WHERE us.userId = :userId AND us.status = :status")
    List<UserSubscription> findByUserIdAndStatus(@Param("userId") UUID userId, @Param("status") UserSubscription.SubscriptionStatus status);
}

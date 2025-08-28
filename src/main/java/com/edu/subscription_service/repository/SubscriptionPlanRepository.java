package com.edu.subscription_service.repository;

import com.edu.subscription_service.entity.SubscriptionPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, UUID> {
    
    List<SubscriptionPlan> findByIsActiveTrue();
    
    @Query("SELECT sp FROM SubscriptionPlan sp WHERE sp.name = :name AND sp.billingCycle = :billingCycle AND sp.isActive = true")
    Optional<SubscriptionPlan> findByNameAndBillingCycleAndIsActiveTrue(
            @Param("name") String name, 
            @Param("billingCycle") SubscriptionPlan.BillingCycle billingCycle
    );
    
    Optional<SubscriptionPlan> findByStripePriceId(String stripePriceId);
}

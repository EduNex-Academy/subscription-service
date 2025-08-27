package com.edu.subscription_service.scheduler;

import com.edu.subscription_service.entity.UserSubscription;
import com.edu.subscription_service.repository.UserSubscriptionRepository;
import com.edu.subscription_service.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class SubscriptionScheduler {
    
    private final SubscriptionService subscriptionService;
    private final UserSubscriptionRepository subscriptionRepository;
    
    /**
     * Process expired subscriptions every hour
     */
    @Scheduled(fixedRate = 3600000) // Run every hour
    public void expireSubscriptions() {
        log.info("Running subscription expiration check");
        try {
            subscriptionService.expireSubscriptions();
            log.info("Subscription expiration check completed");
        } catch (Exception e) {
            log.error("Error during subscription expiration check", e);
        }
    }
    
    /**
     * Clean up and maintenance tasks
     * Runs daily at 2 AM
     */
    @Scheduled(cron = "0 0 2 * * *")
    public void dailyMaintenanceTasks() {
        log.info("Running daily subscription maintenance tasks...");
        
        try {
            // Log subscription statistics
            LocalDateTime now = LocalDateTime.now();
            
            long activeCount = subscriptionRepository.findByStatus(UserSubscription.SubscriptionStatus.ACTIVE).size();
            long pendingCount = subscriptionRepository.findByStatus(UserSubscription.SubscriptionStatus.PENDING).size();
            long expiredCount = subscriptionRepository.findByStatus(UserSubscription.SubscriptionStatus.EXPIRED).size();
            long cancelledCount = subscriptionRepository.findByStatus(UserSubscription.SubscriptionStatus.CANCELLED).size();
            
            log.info("Subscription Stats - Active: {}, Pending: {}, Expired: {}, Cancelled: {}", 
                    activeCount, pendingCount, expiredCount, cancelledCount);
            
            // Clean up old pending subscriptions (older than 24 hours)
            LocalDateTime cutoffTime = now.minusHours(24);
            List<UserSubscription> oldPendingSubscriptions = subscriptionRepository
                    .findByStatus(UserSubscription.SubscriptionStatus.PENDING)
                    .stream()
                    .filter(sub -> sub.getCreatedAt() != null && sub.getCreatedAt().isBefore(cutoffTime))
                    .toList();
            
            for (UserSubscription subscription : oldPendingSubscriptions) {
                log.info("Cleaning up old pending subscription: {} (created: {})", 
                        subscription.getId(), subscription.getCreatedAt());
                subscription.setStatus(UserSubscription.SubscriptionStatus.CANCELLED);
                subscriptionRepository.save(subscription);
            }
            
            if (!oldPendingSubscriptions.isEmpty()) {
                log.info("Cleaned up {} old pending subscriptions", oldPendingSubscriptions.size());
            }
            
            log.info("Daily maintenance completed successfully");
            
        } catch (Exception e) {
            log.error("Error during daily maintenance tasks", e);
        }
    }
}

package com.edu.subscription_service.scheduler;

import com.edu.subscription_service.entity.UserSubscription;
import com.edu.subscription_service.event.SubscriptionPushEvent;
import com.edu.subscription_service.repository.UserSubscriptionRepository;
import com.edu.subscription_service.service.PushNotificationProducer;
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
    private final PushNotificationProducer pushNotificationProducer;

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
     * Send push notifications for subscriptions that will expire in 2 days
     * Runs daily at 9:00am
     */
    @Scheduled(cron = "0 0 9 * * *")
    public void sendExpiryReminders() {
        log.info("Running expiry reminder check (2 days before end)");
        try {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime start = now.plusDays(2).withHour(0).withMinute(0).withSecond(0).withNano(0);
            LocalDateTime end = start.plusDays(1).minusNanos(1);

            List<UserSubscription> expiring = subscriptionRepository.findExpiringBetween(start, end);
            log.info("Found {} subscriptions expiring between {} and {}", expiring.size(), start, end);

            for (UserSubscription sub : expiring) {
                try {
                    SubscriptionPushEvent pushEvent = new SubscriptionPushEvent(
                            sub.getUserId() != null ? sub.getUserId().toString() : null,
                            sub.getId() != null ? sub.getId().toString() : null,
                            "EXPIRY_REMINDER",
                            "Your subscription will expire in 2 days",
                            "EXPIRY_ALERT"
                    );
                    pushNotificationProducer.sendPush(pushEvent);
                    log.info("Sent expiry reminder push for subscription: {} user: {}", sub.getId(), sub.getUserId());
                } catch (Exception ex) {
                    log.error("Failed to send expiry push for subscription: {}", sub.getId(), ex);
                }
            }

            log.info("Expiry reminder check completed");
        } catch (Exception e) {
            log.error("Error during expiry reminder check", e);
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

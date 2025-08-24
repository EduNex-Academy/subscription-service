package com.edu.subscription_service.scheduler;

import com.edu.subscription_service.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class SubscriptionScheduler {
    
    private final SubscriptionService subscriptionService;
    
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
}

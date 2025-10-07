package com.edu.subscription_service.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionEvent {
    private Long userId;
    private String subscriptionId;
    private String eventType;   // e.g., SUBSCRIPTION_RENEWAL
    private String timestamp;
}

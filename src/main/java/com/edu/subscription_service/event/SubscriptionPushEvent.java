package com.edu.subscription_service.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionPushEvent {
    private String userId;
    private String subscriptionId;
    private String eventType;
    private String message;
    private String notificationType; // use String to match notification service types
}

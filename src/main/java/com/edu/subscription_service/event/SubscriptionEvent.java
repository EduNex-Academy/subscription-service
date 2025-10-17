package com.edu.subscription_service.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionEvent {
    private String to; // recipient email
    private String userName;
    private String planName;
    private String planDuration;
    private Double amount; // align with Notification service DTO (Double)
    private String expiryDate;
    private String notificationType; // e.g. SUBSCRIPTION_ACTIVATED, SUBSCRIPTION_CANCELLED
}

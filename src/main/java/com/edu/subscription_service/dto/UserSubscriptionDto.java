package com.edu.subscription_service.dto;

import com.edu.subscription_service.entity.UserSubscription;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserSubscriptionDto {
    private UUID id;
    private UUID userId;
    private SubscriptionPlanDto plan;
    private UserSubscription.SubscriptionStatus status;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private Boolean autoRenew;
    private LocalDateTime createdAt;
}

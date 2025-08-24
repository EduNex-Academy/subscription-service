package com.edu.subscription_service.dto;

import com.edu.subscription_service.entity.SubscriptionPlan;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionPlanDto {
    private UUID id;
    private String name;
    private SubscriptionPlan.BillingCycle billingCycle;
    private BigDecimal price;
    private String currency;
    private Integer pointsAwarded;
    private List<String> features;
    private Boolean isActive;
    private LocalDateTime createdAt;
}

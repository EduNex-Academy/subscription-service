package com.edu.subscription_service.dto.request;

import com.edu.subscription_service.entity.SubscriptionPlan;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateSubscriptionRequest {
    
    @NotNull(message = "Plan ID is required")
    private UUID planId;
    
    @NotNull(message = "Billing cycle is required")
    private SubscriptionPlan.BillingCycle billingCycle;
    
    @Email(message = "Valid email is required for payment processing")
    @NotBlank(message = "Email is required")
    private String email;
    
    private String paymentMethodId; // Stripe payment method ID
}

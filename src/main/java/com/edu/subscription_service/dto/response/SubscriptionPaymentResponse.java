package com.edu.subscription_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionPaymentResponse {

    private String clientSecret;
    private String subscriptionId;
    private String paymentIntentId;
    private String status;
    private BigDecimal amount;
    private String currency;
    private String stripeCustomerId;
    private String stripeSubscriptionId;
}

package com.edu.subscription_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentIntentResponse {
    private String clientSecret;
    private String paymentIntentId;
    private String status;
    private UUID subscriptionId;
    
    // Constructor for backward compatibility (without subscriptionId)
    public PaymentIntentResponse(String clientSecret, String paymentIntentId, String status) {
        this.clientSecret = clientSecret;
        this.paymentIntentId = paymentIntentId;
        this.status = status;
    }
}

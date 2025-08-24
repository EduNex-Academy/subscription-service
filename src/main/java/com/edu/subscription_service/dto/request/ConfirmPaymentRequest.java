package com.edu.subscription_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConfirmPaymentRequest {
    
    @NotBlank(message = "Payment intent ID is required")
    private String paymentIntentId;
    
    @NotBlank(message = "Payment method ID is required")
    private String paymentMethodId;
    
    private boolean savePaymentMethod = false;
}
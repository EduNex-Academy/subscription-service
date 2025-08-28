package com.edu.subscription_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AddPaymentMethodRequest {
    @NotBlank(message = "Payment method ID is required")
    private String paymentMethodId;
    
    private boolean setAsDefault = false;
}

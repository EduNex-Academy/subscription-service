package com.edu.subscription_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentMethodsResponse {
    private List<PaymentMethodInfo> paymentMethods;
    private String defaultPaymentMethodId;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentMethodInfo {
        private String id;
        private String type;
        private String brand;
        private String last4;
        private int expMonth;
        private int expYear;
        private boolean isDefault;
        private String billingName;
        private String country;
    }
}

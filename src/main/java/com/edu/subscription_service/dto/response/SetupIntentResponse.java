package com.edu.subscription_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SetupIntentResponse {
    private String clientSecret;
    private String setupIntentId;
    private String customerId;
}

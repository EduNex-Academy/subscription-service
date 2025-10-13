package com.edu.subscription_service.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateEarningRequest {

    @NotNull(message = "Instructor ID is required")
    private UUID instructorId;

    private UUID courseId;

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    private BigDecimal amount;

    @NotNull(message = "Earning type is required")
    private String earningType;

    private UUID subscriptionId;

    private String description;
}
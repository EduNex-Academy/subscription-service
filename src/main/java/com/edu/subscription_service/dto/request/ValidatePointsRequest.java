package com.edu.subscription_service.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ValidatePointsRequest {

    @NotNull(message = "Points amount is required")
    @Min(value = 1, message = "Points must be at least 1")
    private Integer requiredPoints;

    @NotBlank(message = "Resource type is required (e.g., COURSE_MODULE, QUIZ)")
    private String resourceType;

    @NotNull(message = "Resource ID is required")
    private UUID resourceId;
}


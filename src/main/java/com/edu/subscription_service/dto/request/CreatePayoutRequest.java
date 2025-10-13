package com.edu.subscription_service.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreatePayoutRequest {

    @NotNull(message = "Instructor ID is required")
    private UUID instructorId;

    @NotNull(message = "Period start is required")
    private LocalDateTime periodStart;

    @NotNull(message = "Period end is required")
    private LocalDateTime periodEnd;

    private String description;
}
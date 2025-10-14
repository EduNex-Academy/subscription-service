package com.edu.subscription_service.dto;

import com.edu.subscription_service.entity.InstructorPayout;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InstructorPayoutDto {

    private UUID id;
    private UUID instructorId;
    private BigDecimal amount;
    private String currency;
    private InstructorPayout.PayoutStatus status;
    private LocalDateTime periodStart;
    private LocalDateTime periodEnd;
    private String stripePayoutId;
    private String stripeTransferId;
    private String description;
    private String failureReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime paidAt;
}
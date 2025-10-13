package com.edu.subscription_service.dto;

import com.edu.subscription_service.entity.InstructorEarning;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InstructorEarningDto {

    private UUID id;
    private UUID instructorId;
    private UUID courseId;
    private BigDecimal amount;
    private String currency;
    private InstructorEarning.EarningType earningType;
    private UUID subscriptionId;
    private String description;
    private UUID payoutId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
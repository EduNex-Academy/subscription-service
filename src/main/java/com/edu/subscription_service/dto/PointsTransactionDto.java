package com.edu.subscription_service.dto;

import com.edu.subscription_service.entity.PointsTransaction;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PointsTransactionDto {

    private UUID id;
    private UUID userId;
    private PointsTransaction.TransactionType transactionType;
    private Integer points;
    private String description;
    private String referenceType;
    private UUID referenceId;
    private LocalDateTime createdAt;
}

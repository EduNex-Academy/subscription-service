package com.edu.subscription_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserPointsWalletDto {
    private UUID id;
    private UUID userId;
    private Integer totalPoints;
    private Integer lifetimeEarned;
    private Integer lifetimeSpent;
}

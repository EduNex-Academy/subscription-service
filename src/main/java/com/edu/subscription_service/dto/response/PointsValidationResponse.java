package com.edu.subscription_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PointsValidationResponse {

    private boolean hasEnoughPoints;
    private Integer currentBalance;
    private Integer requiredPoints;
    private String message;

    public static PointsValidationResponse sufficient(Integer currentBalance, Integer requiredPoints) {
        return new PointsValidationResponse(
            true,
            currentBalance,
            requiredPoints,
            "User has enough points"
        );
    }

    public static PointsValidationResponse insufficient(Integer currentBalance, Integer requiredPoints) {
        return new PointsValidationResponse(
            false,
            currentBalance,
            requiredPoints,
            "Insufficient points. You need " + (requiredPoints - currentBalance) + " more points."
        );
    }
}

package com.edu.subscription_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InstructorEarningsResponse {

    private BigDecimal pendingEarnings;
    private BigDecimal totalLifetimeEarnings;
    private BigDecimal totalPaidOut;
    private Integer totalEarningRecords;
    private Integer totalPayouts;

    public static InstructorEarningsResponse of(BigDecimal pendingEarnings, 
                                               BigDecimal totalLifetimeEarnings, 
                                               BigDecimal totalPaidOut,
                                               Integer totalEarningRecords,
                                               Integer totalPayouts) {
        return new InstructorEarningsResponse(
                pendingEarnings, 
                totalLifetimeEarnings, 
                totalPaidOut,
                totalEarningRecords,
                totalPayouts
        );
    }
}
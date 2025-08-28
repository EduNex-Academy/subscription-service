package com.edu.subscription_service.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BillingHistoryResponse {
    private List<BillingHistoryItem> history;
    private int totalCount;
    private boolean hasMore;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BillingHistoryItem {
        private String id;
        private String description;
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime date;
        private BigDecimal amount;
        private String currency;
        private String status;
        private String invoiceUrl;
        private String paymentMethod;
        private String subscriptionName;
    }
}

package com.edu.subscription_service.controller;

import com.edu.subscription_service.service.AuthService;
import com.edu.subscription_service.service.StripeService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PaymentController.class)
class PaymentControllerBillingTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private StripeService stripeService;

    @MockBean
    private AuthService authService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @WithMockUser
    void testGetBillingHistory_Success() throws Exception {
        // Given
        UUID userId = UUID.randomUUID();
        when(authService.extractUserIdAsUUID(any())).thenReturn(userId);
        when(stripeService.findCustomerByUserId(userId)).thenReturn(null);

        // When & Then
        mockMvc.perform(get("/api/v1/payments/billing-history")
                .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.history").isArray())
                .andExpect(jsonPath("$.data.totalCount").exists())
                .andExpect(jsonPath("$.data.hasMore").exists());
    }

    @Test
    @WithMockUser
    void testGetPaymentMethods_Success() throws Exception {
        // Given
        UUID userId = UUID.randomUUID();
        when(authService.extractUserIdAsUUID(any())).thenReturn(userId);
        when(stripeService.findCustomerByUserId(userId)).thenReturn(null);

        // When & Then
        mockMvc.perform(get("/api/v1/payments/payment-methods"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.paymentMethods").isArray())
                .andExpect(jsonPath("$.data.defaultPaymentMethodId").exists());
    }

    @Test
    @WithMockUser
    void testCreateSetupIntent_Success() throws Exception {
        // Given
        UUID userId = UUID.randomUUID();
        when(authService.extractUserIdAsUUID(any())).thenReturn(userId);
        when(stripeService.findCustomerByUserId(userId)).thenReturn(null);

        // When & Then
        mockMvc.perform(post("/api/v1/payments/setup-intent"))
                .andExpect(status().isBadRequest())  // Expected since customer is null
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void testGetBillingHistory_Unauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/payments/billing-history"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testGetPaymentMethods_Unauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/payments/payment-methods"))
                .andExpect(status().isUnauthorized());
    }
}

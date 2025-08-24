package com.edu.subscription_service.controller;

import com.edu.subscription_service.dto.request.ConfirmPaymentRequest;
import com.edu.subscription_service.dto.request.CreatePaymentIntentRequest;
import com.edu.subscription_service.dto.response.ApiResponse;
import com.edu.subscription_service.dto.response.PaymentIntentResponse;
import com.edu.subscription_service.service.AuthService;
import com.edu.subscription_service.service.StripeService;
import com.stripe.exception.StripeException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
@Tag(name = "Payment Management", description = "API endpoints for payment processing with Stripe")
@SecurityRequirement(name = "bearerAuth")
public class PaymentController {

    private final StripeService stripeService;
    private final AuthService authService;

    @PostMapping("/create-intent")
    @Operation(
        summary = "Create payment intent",
        description = "Create a Stripe payment intent for processing payments"
    )
    public ResponseEntity<ApiResponse<PaymentIntentResponse>> createPaymentIntent(
            @Valid @RequestBody CreatePaymentIntentRequest request,
            Authentication authentication) {

        UUID userId = authService.extractUserIdAsUUID(authentication);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Unable to extract user ID from token"));
        }

        try {
            log.info("Creating payment intent for user: {}", userId);

            // Create or get customer
            var customer = stripeService.createCustomer(request.getEmail(), userId);

            // Create payment intent
            PaymentIntentResponse response = stripeService.createPaymentIntent(
                    request.getAmount(),
                    request.getCurrency(),
                    customer.getId(),
                    userId
            );

            return ResponseEntity.ok(ApiResponse.success("Payment intent created successfully", response));

        } catch (StripeException e) {
            log.error("Stripe error while creating payment intent", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("Payment processing error: " + e.getMessage()));
        } catch (Exception e) {
            log.error("Error creating payment intent", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to create payment intent: " + e.getMessage()));
        }
    }

    @PostMapping("/confirm")
    @Operation(
        summary = "Confirm payment",
        description = "Confirm a payment intent after client-side validation"
    )
    public ResponseEntity<ApiResponse<String>> confirmPayment(
            @Valid @RequestBody ConfirmPaymentRequest request,
            Authentication authentication) {

        UUID userId = authService.extractUserIdAsUUID(authentication);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Unable to extract user ID from token"));
        }

        try {
            log.info("Confirming payment for user: {} with payment intent: {}", userId, request.getPaymentIntentId());

            // Note: Payment confirmation is typically handled on the client side with Stripe.js
            // This endpoint is for any server-side confirmation logic if needed

            return ResponseEntity.ok(ApiResponse.success("Payment confirmation initiated", "OK"));

        } catch (Exception e) {
            log.error("Error confirming payment", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to confirm payment: " + e.getMessage()));
        }
    }
}

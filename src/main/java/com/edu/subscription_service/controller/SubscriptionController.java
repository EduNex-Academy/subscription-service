package com.edu.subscription_service.controller;

import com.edu.subscription_service.dto.UserSubscriptionDto;
import com.edu.subscription_service.dto.request.CreateSubscriptionRequest;
import com.edu.subscription_service.dto.response.ApiResponse;
import com.edu.subscription_service.dto.response.PaymentIntentResponse;
import com.edu.subscription_service.service.AuthService;
import com.edu.subscription_service.service.SubscriptionService;
import com.stripe.exception.StripeException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/subscriptions")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
@Tag(name = "Subscription Management", description = "API endpoints for managing user subscriptions and payments")
@SecurityRequirement(name = "bearerAuth")
public class SubscriptionController {
    
    private final SubscriptionService subscriptionService;
    private final AuthService authService;
    
    // Simple request tracking to prevent duplicate setup requests
    private final java.util.concurrent.ConcurrentHashMap<String, Long> activeSetupRequests = new java.util.concurrent.ConcurrentHashMap<>();
    private static final long REQUEST_TIMEOUT = 30000; // 30 seconds
    
    @PostMapping("/create")
    @Operation(
        summary = "Create new subscription",
        description = "Create a new subscription for a user, processing payment through Stripe"
    )
    public ResponseEntity<ApiResponse<PaymentIntentResponse>> createSubscription(
            @Valid @RequestBody CreateSubscriptionRequest request,
            @Parameter(hidden = true) Authentication authentication) {
        UUID userId = authService.extractUserIdAsUUID(authentication);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Unable to extract user ID from token"));
        }
        
        try {
            log.info("Creating subscription for user: {}", userId);
            PaymentIntentResponse response = subscriptionService.createSubscription(userId, request);
            return ResponseEntity.ok(ApiResponse.success("Subscription created successfully", response));
        } catch (StripeException e) {
            log.error("Stripe error while creating subscription", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("Payment processing error: " + e.getMessage()));
        } catch (Exception e) {
            log.error("Error creating subscription", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to create subscription: " + e.getMessage()));
        }
    }
    
    @PostMapping("/setup")
    @Operation(
        summary = "Setup subscription payment method",
        description = "Create a setup intent for collecting payment method before subscription creation"
    )
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> setupSubscription(
            @Valid @RequestBody CreateSubscriptionRequest request,
            @Parameter(hidden = true) Authentication authentication) {
        UUID userId = authService.extractUserIdAsUUID(authentication);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Unable to extract user ID from token"));
        }
        
        // Prevent duplicate requests
        String requestKey = userId + "_" + request.getPlanId();
        Long existingRequest = activeSetupRequests.get(requestKey);
        if (existingRequest != null && (System.currentTimeMillis() - existingRequest) < REQUEST_TIMEOUT) {
            log.warn("Duplicate setup request blocked for user: {} within timeout window", userId);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(ApiResponse.error("Setup request already in progress. Please wait."));
        }
        
        activeSetupRequests.put(requestKey, System.currentTimeMillis());
        
        try {
            log.info("Setting up subscription for user: {} with plan: {}", userId, request.getPlanId());
            java.util.Map<String, Object> response = subscriptionService.createSubscriptionSetup(request, userId);
            return ResponseEntity.ok(ApiResponse.success("Subscription setup created successfully", response));
        } catch (Exception e) {
            log.error("Error setting up subscription", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to setup subscription: " + e.getMessage()));
        } finally {
            // Clean up after some time to allow retry
            new Thread(() -> {
                try {
                    Thread.sleep(5000); // 5 seconds
                    activeSetupRequests.remove(requestKey);
                } catch (InterruptedException ignored) {}
            }).start();
        }
    }
    
    @PostMapping("/complete")
    @Operation(
        summary = "Complete subscription after payment setup",
        description = "Complete subscription creation after successful payment method setup"
    )
    public ResponseEntity<ApiResponse<UserSubscriptionDto>> completeSubscription(
            @RequestParam String customerId,
            @RequestParam UUID planId,
            @Parameter(hidden = true) Authentication authentication) {
        UUID userId = authService.extractUserIdAsUUID(authentication);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Unable to extract user ID from token"));
        }
        
        try {
            log.info("Completing subscription for user: {}", userId);
            UserSubscriptionDto response = subscriptionService.createSubscriptionAfterSetup(customerId, planId, userId);
            return ResponseEntity.ok(ApiResponse.success("Subscription completed successfully", response));
        } catch (Exception e) {
            log.error("Error completing subscription", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to complete subscription: " + e.getMessage()));
        }
    }
    
    // Commenting out manual activation - subscriptions should be activated automatically by Stripe webhooks
    /*
    @PostMapping("/{subscriptionId}/activate")
    @Operation(
        summary = "Activate subscription",
        description = "Activate a subscription after successful payment confirmation"
    )
    public ResponseEntity<ApiResponse<String>> activateSubscription(
            @Parameter(description = "Subscription ID to activate", required = true, example = "550e8400-e29b-41d4-a716-446655440000")
            @PathVariable UUID subscriptionId)
    {
        try {
            log.info("Activating subscription: {}", subscriptionId);
            subscriptionService.activateSubscription(subscriptionId);
            return ResponseEntity.ok(ApiResponse.success("Subscription activated successfully", "OK"));
        } catch (Exception e) {
            log.error("Error activating subscription", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to activate subscription: " + e.getMessage()));
        }
    }
    */
    
    @PostMapping("/{subscriptionId}/cancel")
    @Operation(
        summary = "Cancel subscription",
        description = "Cancel an active subscription and handle refund processing if applicable"
    )
    public ResponseEntity<ApiResponse<String>> cancelSubscription(
            @Parameter(description = "Subscription ID to cancel", required = true, example = "550e8400-e29b-41d4-a716-446655440000")
            @PathVariable UUID subscriptionId)
    {
        try {
            log.info("Cancelling subscription: {}", subscriptionId);
            subscriptionService.cancelSubscription(subscriptionId);
            return ResponseEntity.ok(ApiResponse.success("Subscription cancelled successfully", "OK"));
        } catch (StripeException e) {
            log.error("Stripe error while cancelling subscription", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("Payment processing error: " + e.getMessage()));
        } catch (Exception e) {
            log.error("Error cancelling subscription", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to cancel subscription: " + e.getMessage()));
        }
    }
    
    @GetMapping("/user")
    @PreAuthorize("hasAnyRole('STUDENT', 'ADMIN')")
    @Operation(
        summary = "Get user subscriptions",
        description = "Retrieve all subscriptions for the current authenticated user"
    )
    public ResponseEntity<ApiResponse<List<UserSubscriptionDto>>> getUserSubscriptions(
            @Parameter(hidden = true) Authentication authentication) {
        UUID userId = authService.extractUserIdAsUUID(authentication);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Unable to extract user ID from token"));
        }
        
        log.info("Fetching subscriptions for user: {}", userId);
        List<UserSubscriptionDto> subscriptions = subscriptionService.getUserSubscriptions(userId);
        return ResponseEntity.ok(ApiResponse.success("Retrieved user subscriptions", subscriptions));
    }
    
    @GetMapping("/user/active")
    @PreAuthorize("hasAnyRole('STUDENT', 'ADMIN')")
    @Operation(
        summary = "Get active subscription",
        description = "Retrieve the current active subscription for the authenticated user"
    )
    public ResponseEntity<ApiResponse<UserSubscriptionDto>> getActiveSubscription(
            @Parameter(hidden = true) Authentication authentication) {
        UUID userId = authService.extractUserIdAsUUID(authentication);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Unable to extract user ID from token"));
        }
        
        log.info("Fetching active subscription for user: {}", userId);
        return subscriptionService.getActiveSubscription(userId)
                .map(subscription -> ResponseEntity.ok(ApiResponse.success("Retrieved active subscription", subscription)))
                .orElse(ResponseEntity.ok(ApiResponse.success("No active subscription found", null)));
    }
    
    @GetMapping("/debug/{planId}")
    @Operation(
        summary = "Debug subscription plan",
        description = "Debug endpoint to check plan details and Stripe price mapping"
    )
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> debugPlan(
            @PathVariable UUID planId) {
        try {
            log.info("Debugging plan: {}", planId);
            java.util.Map<String, Object> debugInfo = subscriptionService.debugPlan(planId);
            return ResponseEntity.ok(ApiResponse.success("Plan debug info", debugInfo));
        } catch (Exception e) {
            log.error("Error debugging plan", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to debug plan: " + e.getMessage()));
        }
    }
}

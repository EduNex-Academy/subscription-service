package com.edu.subscription_service.controller;

import com.edu.subscription_service.dto.SubscriptionPlanDto;
import com.edu.subscription_service.dto.response.ApiResponse;
import com.edu.subscription_service.service.SubscriptionPlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/subscription-plans")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Subscription Plans", description = "Public API endpoints for browsing available subscription plans")
public class SubscriptionPlanController {
    
    private final SubscriptionPlanService subscriptionPlanService;
    
    @GetMapping
    @Operation(
        summary = "Get all subscription plans",
        description = "Retrieve all active subscription plans available for purchase. No authentication required."
    )
    public ResponseEntity<ApiResponse<List<SubscriptionPlanDto>>> getAllPlans() {
        log.info("Fetching all subscription plans");
        List<SubscriptionPlanDto> plans = subscriptionPlanService.getAllActivePlans();
        return ResponseEntity.ok(ApiResponse.success("Retrieved all subscription plans", plans));
    }
    
    @GetMapping("/{planId}")
    @Operation(
        summary = "Get subscription plan by ID",
        description = "Retrieve a specific subscription plan by its unique identifier. No authentication required."
    )
    public ResponseEntity<ApiResponse<SubscriptionPlanDto>> getPlanById(
            @Parameter(description = "Subscription plan ID to retrieve", required = true, example = "550e8400-e29b-41d4-a716-446655440000")
            @PathVariable UUID planId)
    {
        log.info("Fetching subscription plan with id: {}", planId);
        return subscriptionPlanService.getPlanById(planId)
                .map(plan -> ResponseEntity.ok(ApiResponse.success("Retrieved subscription plan", plan)))
                .orElse(ResponseEntity.notFound().build());
    }
    
    @GetMapping("/by-name/{planName}")
    @Operation(
        summary = "Get subscription plans by name",
        description = "Retrieve subscription plans that match the specified plan name. Useful for filtering plans by type or category. No authentication required."
    )
    public ResponseEntity<ApiResponse<List<SubscriptionPlanDto>>> getPlansByName(
            @Parameter(description = "Plan name to search for", required = true, example = "Premium")
            @PathVariable String planName)
    {
        log.info("Fetching subscription plans for name: {}", planName);
        List<SubscriptionPlanDto> plans = subscriptionPlanService.getPlansByName(planName);
        return ResponseEntity.ok(ApiResponse.success("Retrieved subscription plans by name", plans));
    }
}

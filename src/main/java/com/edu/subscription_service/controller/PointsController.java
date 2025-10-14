package com.edu.subscription_service.controller;

import com.edu.subscription_service.dto.PointsTransactionDto;
import com.edu.subscription_service.dto.UserPointsWalletDto;
import com.edu.subscription_service.dto.request.RedeemPointsRequest;
import com.edu.subscription_service.dto.response.ApiResponse;
import com.edu.subscription_service.dto.response.PointsValidationResponse;
import com.edu.subscription_service.service.AuthService;
import com.edu.subscription_service.service.PointsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/points")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Points Management", description = "API endpoints for managing user points and rewards")
@SecurityRequirement(name = "bearerAuth")
public class PointsController {
    
    private final PointsService pointsService;
    private final AuthService authService;
    
    @GetMapping("/wallet")
    @PreAuthorize("hasAnyRole('STUDENT', 'ADMIN')")
    @Operation(
        summary = "Get user points wallet",
        description = "Retrieve the current user's points wallet information including balance and details"
    )
    public ResponseEntity<ApiResponse<UserPointsWalletDto>> getUserWallet(
            @Parameter(hidden = true) Authentication authentication) {
        UUID userId = authService.extractUserIdAsUUID(authentication);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Unable to extract user ID from token"));
        }
        
        log.info("Fetching wallet for user: {}", userId);
        return pointsService.getUserWallet(userId)
                .map(wallet -> ResponseEntity.ok(ApiResponse.success("Retrieved user wallet", wallet)))
                .orElse(ResponseEntity.ok(ApiResponse.success("Retrieved user wallet", pointsService.getOrCreateWallet(userId))));
    }
    
    @GetMapping("/balance")
    @PreAuthorize("hasAnyRole('STUDENT', 'ADMIN')")
    @Operation(
        summary = "Get user points balance",
        description = "Get the current points balance for the user"
    )
    public ResponseEntity<ApiResponse<Integer>> getUserBalance(
            @Parameter(hidden = true) Authentication authentication) {
        UUID userId = authService.extractUserIdAsUUID(authentication);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Unable to extract user ID from token"));
        }

        Integer balance = pointsService.getUserPointsBalance(userId);
        return ResponseEntity.ok(ApiResponse.success("Retrieved points balance", balance));
    }

    @PostMapping("/validate")
    @PreAuthorize("hasAnyRole('STUDENT', 'ADMIN', 'INSTRUCTOR')")
    @Operation(
        summary = "Validate user has enough points",
        description = "Check if user has sufficient points for a resource. Used by Course Service before granting access."
    )
    public ResponseEntity<ApiResponse<PointsValidationResponse>> validatePoints(
            @RequestParam @Parameter(description = "User ID to validate") UUID userId,
            @RequestParam @Parameter(description = "Required points amount") Integer requiredPoints) {

        log.info("Validating points for user: {} - Required: {}", userId, requiredPoints);

        PointsValidationResponse validation = pointsService.validatePoints(userId, requiredPoints);

        if (validation.isHasEnoughPoints()) {
            return ResponseEntity.ok(ApiResponse.success("User has enough points", validation));
        } else {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(validation.getMessage(), validation));
        }
    }

    @PostMapping("/deduct")
    @PreAuthorize("hasAnyRole('STUDENT', 'ADMIN', 'INSTRUCTOR')")
    @Operation(
        summary = "Deduct points for resource access",
        description = "Deduct points when user accesses a course module or quiz. Called by Course Service."
    )
    public ResponseEntity<ApiResponse<String>> deductPoints(
            @RequestParam @Parameter(description = "User ID") UUID userId,
            @RequestParam @Parameter(description = "Points to deduct") Integer points,
            @RequestParam @Parameter(description = "Resource type (e.g., COURSE_MODULE, QUIZ)") String resourceType,
            @RequestParam @Parameter(description = "Resource ID") UUID resourceId,
            @RequestParam @Parameter(description = "Description") String description) {

        log.info("Deducting {} points from user: {} for {} - {}", points, userId, resourceType, resourceId);

        boolean success = pointsService.deductPointsForResource(userId, points, resourceType, resourceId, description);

        if (success) {
            return ResponseEntity.ok(ApiResponse.success("Points deducted successfully", "OK"));
        } else {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("Insufficient points or wallet not found"));
        }
    }

    @PostMapping("/redeem")
    @PreAuthorize("hasAnyRole('STUDENT', 'ADMIN')")
    @Operation(
        summary = "Redeem points",
        description = "Redeem points for course material. Points will be deducted from user's wallet"
    )
    public ResponseEntity<ApiResponse<String>> redeemPoints(
            @Valid @RequestBody RedeemPointsRequest request,
            @Parameter(hidden = true) Authentication authentication) {
        UUID userId = authService.extractUserIdAsUUID(authentication);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Unable to extract user ID from token"));
        }
        
        try {
            log.info("Redeeming points for user: {}", userId);
            boolean success = pointsService.redeemPoints(userId, request);
            
            if (success) {
                return ResponseEntity.ok(ApiResponse.success("Points redeemed successfully", "OK"));
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(ApiResponse.error("Insufficient points or invalid request"));
            }
        } catch (Exception e) {
            log.error("Error redeeming points", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to redeem points: " + e.getMessage()));
        }
    }
    
    @GetMapping("/transactions")
    @PreAuthorize("hasAnyRole('STUDENT', 'ADMIN')")
    @Operation(
        summary = "Get user transaction history",
        description = "Retrieve paginated transaction history for the current user's points"
    )
    public ResponseEntity<ApiResponse<Page<PointsTransactionDto>>> getUserTransactionHistory(
            @Parameter(description = "Page number (0-based)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Number of items per page", example = "20")
            @RequestParam(defaultValue = "20") int size,
            @Parameter(hidden = true) Authentication authentication) {

        UUID userId = authService.extractUserIdAsUUID(authentication);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Unable to extract user ID from token"));
        }
        
        Pageable pageable = PageRequest.of(page, size);
        Page<PointsTransactionDto> transactions = pointsService.getUserTransactionHistory(userId, pageable);

        return ResponseEntity.ok(ApiResponse.success("Retrieved transaction history", transactions));
    }
    
    @PostMapping("/award")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    @Operation(
        summary = "Award points to user",
        description = "Admin only endpoint to award points to a specific user. Creates a positive transaction record"
    )
    public ResponseEntity<ApiResponse<String>> awardPoints(
            @Parameter(description = "User ID to award points to", required = true, example = "550e8400-e29b-41d4-a716-446655440000")
            @RequestParam UUID userId,
            @Parameter(description = "Number of points to award", required = true, example = "100")
            @RequestParam Integer points,
            @Parameter(description = "Description/reason for awarding points", required = true, example = "Course completion bonus")
            @RequestParam String description,
            @Parameter(description = "Reference type for the award", example = "COURSE_COMPLETION")
            @RequestParam(required = false) String referenceType,
            @Parameter(description = "Reference ID related to the award", example = "550e8400-e29b-41d4-a716-446655440001")
            @RequestParam(required = false) UUID referenceId)
    {
        try {
            log.info("Awarding {} points to user: {}", points, userId);
            pointsService.awardPoints(userId, points, description, referenceType, referenceId);
            return ResponseEntity.ok(ApiResponse.success("Points awarded successfully", "OK"));
        } catch (Exception e) {
            log.error("Error awarding points", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to award points: " + e.getMessage()));
        }
    }
}

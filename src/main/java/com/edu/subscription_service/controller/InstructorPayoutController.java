package com.edu.subscription_service.controller;

import com.edu.subscription_service.dto.response.ApiResponse;
import com.edu.subscription_service.entity.InstructorEarning;
import com.edu.subscription_service.entity.InstructorPayout;
import com.edu.subscription_service.service.AuthService;
import com.edu.subscription_service.service.InstructorPayoutService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/instructor/payouts")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Instructor Payouts", description = "API endpoints for managing instructor earnings and payouts")
@SecurityRequirement(name = "bearerAuth")
public class InstructorPayoutController {

    private final InstructorPayoutService payoutService;
    private final AuthService authService;

    @GetMapping("/earnings")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    @Operation(
        summary = "Get instructor earnings",
        description = "Retrieve paginated earnings history for the current instructor"
    )
    public ResponseEntity<ApiResponse<Page<InstructorEarning>>> getEarnings(
            @Parameter(description = "Page number (0-based)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Number of items per page", example = "20")
            @RequestParam(defaultValue = "20") int size,
            @Parameter(hidden = true) Authentication authentication) {

        UUID instructorId = authService.extractUserIdAsUUID(authentication);
        if (instructorId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Unable to extract user ID from token"));
        }

        Pageable pageable = PageRequest.of(page, size);
        Page<InstructorEarning> earnings = payoutService.getInstructorEarnings(instructorId, pageable);

        return ResponseEntity.ok(ApiResponse.success("Retrieved earnings history", earnings));
    }

    @GetMapping("/pending")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    @Operation(
        summary = "Get pending earnings",
        description = "Get the total amount of earnings pending payout"
    )
    public ResponseEntity<ApiResponse<BigDecimal>> getPendingEarnings(
            @Parameter(hidden = true) Authentication authentication) {

        UUID instructorId = authService.extractUserIdAsUUID(authentication);
        if (instructorId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Unable to extract user ID from token"));
        }

        BigDecimal pending = payoutService.getPendingEarnings(instructorId);
        return ResponseEntity.ok(ApiResponse.success("Retrieved pending earnings", pending));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    @Operation(
        summary = "Get payout history",
        description = "Retrieve paginated payout history for the current instructor"
    )
    public ResponseEntity<ApiResponse<Page<InstructorPayout>>> getPayouts(
            @Parameter(description = "Page number (0-based)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Number of items per page", example = "20")
            @RequestParam(defaultValue = "20") int size,
            @Parameter(hidden = true) Authentication authentication) {

        UUID instructorId = authService.extractUserIdAsUUID(authentication);
        if (instructorId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Unable to extract user ID from token"));
        }

        Pageable pageable = PageRequest.of(page, size);
        Page<InstructorPayout> payouts = payoutService.getInstructorPayouts(instructorId, pageable);

        return ResponseEntity.ok(ApiResponse.success("Retrieved payout history", payouts));
    }

    @PostMapping("/record-earning")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
        summary = "Record instructor earning",
        description = "Admin endpoint to record earnings for an instructor"
    )
    public ResponseEntity<ApiResponse<String>> recordEarning(
            @RequestParam @Parameter(description = "Instructor ID") UUID instructorId,
            @RequestParam(required = false) @Parameter(description = "Course ID") UUID courseId,
            @RequestParam @Parameter(description = "Amount to record") BigDecimal amount,
            @RequestParam @Parameter(description = "Earning type") InstructorEarning.EarningType earningType,
            @RequestParam @Parameter(description = "Description") String description) {

        try {
            payoutService.recordEarning(instructorId, courseId, amount, earningType, description);
            return ResponseEntity.ok(ApiResponse.success("Earning recorded successfully", "OK"));
        } catch (Exception e) {
            log.error("Error recording earning", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to record earning: " + e.getMessage()));
        }
    }

    @PostMapping("/create")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
        summary = "Create payout",
        description = "Admin endpoint to create a payout for an instructor for a specific period"
    )
    public ResponseEntity<ApiResponse<InstructorPayout>> createPayout(
            @RequestParam @Parameter(description = "Instructor ID") UUID instructorId,
            @RequestParam @Parameter(description = "Period start date")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime periodStart,
            @RequestParam @Parameter(description = "Period end date")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime periodEnd) {

        try {
            InstructorPayout payout = payoutService.createPayout(instructorId, periodStart, periodEnd);
            return ResponseEntity.ok(ApiResponse.success("Payout created successfully", payout));
        } catch (Exception e) {
            log.error("Error creating payout", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to create payout: " + e.getMessage()));
        }
    }

    @PutMapping("/{payoutId}/process")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
        summary = "Process payout",
        description = "Mark payout as processing and set Stripe payout ID"
    )
    public ResponseEntity<ApiResponse<String>> processPayout(
            @PathVariable @Parameter(description = "Payout ID") UUID payoutId,
            @RequestParam @Parameter(description = "Stripe payout ID") String stripePayoutId) {

        try {
            payoutService.processPayout(payoutId, stripePayoutId);
            return ResponseEntity.ok(ApiResponse.success("Payout processing", "OK"));
        } catch (Exception e) {
            log.error("Error processing payout", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to process payout: " + e.getMessage()));
        }
    }

    @PutMapping("/{payoutId}/complete")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
        summary = "Complete payout",
        description = "Mark payout as completed"
    )
    public ResponseEntity<ApiResponse<String>> completePayout(
            @PathVariable @Parameter(description = "Payout ID") UUID payoutId) {

        try {
            payoutService.completePayout(payoutId);
            return ResponseEntity.ok(ApiResponse.success("Payout completed", "OK"));
        } catch (Exception e) {
            log.error("Error completing payout", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to complete payout: " + e.getMessage()));
        }
    }

    @PutMapping("/{payoutId}/fail")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
        summary = "Fail payout",
        description = "Mark payout as failed with a reason"
    )
    public ResponseEntity<ApiResponse<String>> failPayout(
            @PathVariable @Parameter(description = "Payout ID") UUID payoutId,
            @RequestParam @Parameter(description = "Failure reason") String reason) {

        try {
            payoutService.failPayout(payoutId, reason);
            return ResponseEntity.ok(ApiResponse.success("Payout marked as failed", "OK"));
        } catch (Exception e) {
            log.error("Error failing payout", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to update payout: " + e.getMessage()));
        }
    }
}


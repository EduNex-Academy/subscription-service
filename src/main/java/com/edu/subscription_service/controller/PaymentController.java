package com.edu.subscription_service.controller;

import com.edu.subscription_service.dto.request.AddPaymentMethodRequest;
import com.edu.subscription_service.dto.request.ConfirmPaymentRequest;
import com.edu.subscription_service.dto.request.CreatePaymentIntentRequest;
import com.edu.subscription_service.dto.response.ApiResponse;
import com.edu.subscription_service.dto.response.BillingHistoryResponse;
import com.edu.subscription_service.dto.response.PaymentIntentResponse;
import com.edu.subscription_service.dto.response.PaymentMethodsResponse;
import com.edu.subscription_service.dto.response.SetupIntentResponse;
import com.edu.subscription_service.service.AuthService;
import com.edu.subscription_service.service.StripeService;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.Invoice;
import com.stripe.model.PaymentMethod;
import com.stripe.model.SetupIntent;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Slf4j
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
    
    @GetMapping("/billing-history")
    @Operation(
        summary = "Get billing history",
        description = "Retrieve billing history and past payments from Stripe"
    )
    public ResponseEntity<ApiResponse<BillingHistoryResponse>> getBillingHistory(
            @RequestParam(defaultValue = "50") int limit,
            Authentication authentication) {

        UUID userId = authService.extractUserIdAsUUID(authentication);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Unable to extract user ID from token"));
        }

        try {
            log.info("Fetching billing history for user: {}", userId);

            // Get customer from Stripe
            Customer customer = stripeService.findCustomerByUserId(userId);
            if (customer == null) {
                return ResponseEntity.ok(ApiResponse.success("No billing history found", 
                    BillingHistoryResponse.builder()
                        .history(new ArrayList<>())
                        .totalCount(0)
                        .hasMore(false)
                        .build()));
            }

            // Get invoices from Stripe
            var invoices = stripeService.getBillingHistory(customer.getId(), limit);
            
            List<BillingHistoryResponse.BillingHistoryItem> historyItems = new ArrayList<>();
            
            for (Invoice invoice : invoices.getData()) {
                BillingHistoryResponse.BillingHistoryItem item = BillingHistoryResponse.BillingHistoryItem.builder()
                    .id(invoice.getId())
                    .description(invoice.getDescription() != null ? invoice.getDescription() : "Subscription Payment")
                    .date(LocalDateTime.ofInstant(Instant.ofEpochSecond(invoice.getCreated()), ZoneId.systemDefault()))
                    .amount(BigDecimal.valueOf(invoice.getAmountPaid()).divide(BigDecimal.valueOf(100)))
                    .currency(invoice.getCurrency().toUpperCase())
                    .status(invoice.getStatus())
                    .invoiceUrl(invoice.getHostedInvoiceUrl())
                    .paymentMethod(getPaymentMethodDescription(invoice))
                    .subscriptionName(getSubscriptionName(invoice))
                    .build();
                
                historyItems.add(item);
            }

            BillingHistoryResponse response = BillingHistoryResponse.builder()
                .history(historyItems)
                .totalCount(historyItems.size())
                .hasMore(invoices.getHasMore())
                .build();

            return ResponseEntity.ok(ApiResponse.success("Billing history retrieved successfully", response));

        } catch (StripeException e) {
            log.error("Stripe error while fetching billing history", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("Failed to fetch billing history: " + e.getMessage()));
        } catch (Exception e) {
            log.error("Error fetching billing history", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to fetch billing history: " + e.getMessage()));
        }
    }
    
    @GetMapping("/payment-methods")
    @Operation(
        summary = "Get payment methods",
        description = "Retrieve user's payment methods from Stripe"
    )
    public ResponseEntity<ApiResponse<PaymentMethodsResponse>> getPaymentMethods(
            Authentication authentication) {

        UUID userId = authService.extractUserIdAsUUID(authentication);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Unable to extract user ID from token"));
        }

        try {
            log.info("Fetching payment methods for user: {}", userId);

            // Get customer from Stripe
            Customer customer = stripeService.findCustomerByUserId(userId);
            if (customer == null) {
                return ResponseEntity.ok(ApiResponse.success("No payment methods found", 
                    PaymentMethodsResponse.builder()
                        .paymentMethods(new ArrayList<>())
                        .defaultPaymentMethodId(null)
                        .build()));
            }

            // Get payment methods from Stripe
            var paymentMethods = stripeService.getPaymentMethods(customer.getId());
            
            List<PaymentMethodsResponse.PaymentMethodInfo> methodInfos = new ArrayList<>();
            String defaultPaymentMethodId = customer.getInvoiceSettings() != null ? 
                customer.getInvoiceSettings().getDefaultPaymentMethod() : null;
            
            for (PaymentMethod pm : paymentMethods.getData()) {
                if (pm.getCard() != null) {
                    PaymentMethodsResponse.PaymentMethodInfo info = PaymentMethodsResponse.PaymentMethodInfo.builder()
                        .id(pm.getId())
                        .type(pm.getType())
                        .brand(pm.getCard().getBrand())
                        .last4(pm.getCard().getLast4())
                        .expMonth(Math.toIntExact(pm.getCard().getExpMonth()))
                        .expYear(Math.toIntExact(pm.getCard().getExpYear()))
                        .isDefault(pm.getId().equals(defaultPaymentMethodId))
                        .billingName(pm.getBillingDetails() != null ? pm.getBillingDetails().getName() : null)
                        .country(pm.getCard().getCountry())
                        .build();
                    
                    methodInfos.add(info);
                }
            }

            PaymentMethodsResponse response = PaymentMethodsResponse.builder()
                .paymentMethods(methodInfos)
                .defaultPaymentMethodId(defaultPaymentMethodId)
                .build();

            return ResponseEntity.ok(ApiResponse.success("Payment methods retrieved successfully", response));

        } catch (StripeException e) {
            log.error("Stripe error while fetching payment methods", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("Failed to fetch payment methods: " + e.getMessage()));
        } catch (Exception e) {
            log.error("Error fetching payment methods", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to fetch payment methods: " + e.getMessage()));
        }
    }
    
    @PostMapping("/setup-intent")
    @Operation(
        summary = "Create setup intent",
        description = "Create a Stripe setup intent for adding new payment methods"
    )
    public ResponseEntity<ApiResponse<SetupIntentResponse>> createSetupIntent(
            Authentication authentication) {

        UUID userId = authService.extractUserIdAsUUID(authentication);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Unable to extract user ID from token"));
        }

        try {
            log.info("Creating setup intent for user: {}", userId);

            // Get or create customer
            Customer customer = stripeService.findCustomerByUserId(userId);
            if (customer == null) {
                // This shouldn't happen in normal flow, but let's handle it
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("Customer not found. Please complete your profile first."));
            }

            // Create setup intent
            SetupIntent setupIntent = stripeService.createSetupIntent(customer.getId());

            SetupIntentResponse response = SetupIntentResponse.builder()
                .clientSecret(setupIntent.getClientSecret())
                .setupIntentId(setupIntent.getId())
                .customerId(customer.getId())
                .build();

            return ResponseEntity.ok(ApiResponse.success("Setup intent created successfully", response));

        } catch (StripeException e) {
            log.error("Stripe error while creating setup intent", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("Failed to create setup intent: " + e.getMessage()));
        } catch (Exception e) {
            log.error("Error creating setup intent", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to create setup intent: " + e.getMessage()));
        }
    }
    
    @PostMapping("/payment-methods")
    @Operation(
        summary = "Add payment method",
        description = "Attach a payment method to customer and optionally set as default"
    )
    public ResponseEntity<ApiResponse<String>> addPaymentMethod(
            @Valid @RequestBody AddPaymentMethodRequest request,
            Authentication authentication) {

        UUID userId = authService.extractUserIdAsUUID(authentication);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Unable to extract user ID from token"));
        }

        try {
            log.info("Adding payment method for user: {} with payment method: {}", userId, request.getPaymentMethodId());

            // Get customer
            Customer customer = stripeService.findCustomerByUserId(userId);
            if (customer == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("Customer not found. Please complete your profile first."));
            }

            // Attach payment method to customer
            stripeService.attachPaymentMethod(request.getPaymentMethodId(), customer.getId());

            // Set as default if requested
            if (request.isSetAsDefault()) {
                stripeService.setDefaultPaymentMethod(customer.getId(), request.getPaymentMethodId());
            }

            return ResponseEntity.ok(ApiResponse.success("Payment method added successfully", "OK"));

        } catch (StripeException e) {
            log.error("Stripe error while adding payment method", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("Failed to add payment method: " + e.getMessage()));
        } catch (Exception e) {
            log.error("Error adding payment method", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to add payment method: " + e.getMessage()));
        }
    }
    
    @DeleteMapping("/payment-methods/{paymentMethodId}")
    @Operation(
        summary = "Remove payment method",
        description = "Detach a payment method from customer"
    )
    public ResponseEntity<ApiResponse<String>> removePaymentMethod(
            @PathVariable String paymentMethodId,
            Authentication authentication) {

        UUID userId = authService.extractUserIdAsUUID(authentication);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Unable to extract user ID from token"));
        }

        try {
            log.info("Removing payment method {} for user: {}", paymentMethodId, userId);

            // Detach payment method
            stripeService.detachPaymentMethod(paymentMethodId);

            return ResponseEntity.ok(ApiResponse.success("Payment method removed successfully", "OK"));

        } catch (StripeException e) {
            log.error("Stripe error while removing payment method", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("Failed to remove payment method: " + e.getMessage()));
        } catch (Exception e) {
            log.error("Error removing payment method", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to remove payment method: " + e.getMessage()));
        }
    }
    
    @PutMapping("/payment-methods/{paymentMethodId}/default")
    @Operation(
        summary = "Set default payment method",
        description = "Set a payment method as the default for the customer"
    )
    public ResponseEntity<ApiResponse<String>> setDefaultPaymentMethod(
            @PathVariable String paymentMethodId,
            Authentication authentication) {

        UUID userId = authService.extractUserIdAsUUID(authentication);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Unable to extract user ID from token"));
        }

        try {
            log.info("Setting default payment method {} for user: {}", paymentMethodId, userId);

            // Get customer
            Customer customer = stripeService.findCustomerByUserId(userId);
            if (customer == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("Customer not found"));
            }

            // Set as default
            stripeService.setDefaultPaymentMethod(customer.getId(), paymentMethodId);

            return ResponseEntity.ok(ApiResponse.success("Default payment method updated successfully", "OK"));

        } catch (StripeException e) {
            log.error("Stripe error while setting default payment method", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("Failed to set default payment method: " + e.getMessage()));
        } catch (Exception e) {
            log.error("Error setting default payment method", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to set default payment method: " + e.getMessage()));
        }
    }
    
    private String getPaymentMethodDescription(Invoice invoice) {
        try {
            if (invoice.getCharge() != null && !invoice.getCharge().isEmpty()) {
                // If we have charge info, we could expand it to get payment method details
                return "Card";
            }
            return "Unknown";
        } catch (Exception e) {
            return "Unknown";
        }
    }
    
    private String getSubscriptionName(Invoice invoice) {
        try {
            if (invoice.getSubscription() != null) {
                return "Subscription";
            }
            return "One-time Payment";
        } catch (Exception e) {
            return "Unknown";
        }
    }
}

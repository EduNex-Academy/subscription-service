package com.edu.subscription_service.controller;

import com.edu.subscription_service.service.WebhookService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.net.Webhook;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/webhooks")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Webhooks", description = "Webhook endpoints for external service integrations")
public class WebhookController {
    
    private final WebhookService webhookService;
    
    @Value("${stripe.webhook.secret}")
    private String webhookSecret;
    
    @PostMapping(value = "/stripe", consumes = "application/json")
    @Operation(
        summary = "Handle Stripe webhook events",
        description = "Webhook endpoint for processing Stripe events as JSON. Verifies signature and processes subscription and payment events."
    )
    public ResponseEntity<String> handleStripeWebhook(
            @Parameter(description = "Raw JSON webhook payload from Stripe", required = true)
            @RequestBody String payload,
            @Parameter(description = "Stripe signature header for webhook verification", required = true)
            @RequestHeader("Stripe-Signature") String sigHeader)
    {
        log.info("Received Stripe webhook event");
        
        try {
            // Construct event from JSON payload and verify signature
            com.stripe.model.Event event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
            log.info("Webhook signature verified successfully. Event type: {}", event.getType());
            
            // Process the event
            webhookService.handleStripeEvent(event);
            
            return ResponseEntity.ok("Webhook handled successfully");
            
        } catch (SignatureVerificationException e) {
            log.error("Invalid Stripe signature", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid signature");
        } catch (Exception e) {
            log.error("Error handling Stripe webhook", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Webhook handling failed: " + e.getMessage());
        }
    }
}

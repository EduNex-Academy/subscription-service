package com.edu.subscription_service.controller;

import com.edu.subscription_service.service.WebhookService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
    
    @PostMapping("/stripe")
    @Operation(
        summary = "Handle Stripe webhook events",
        description = "Webhook endpoint for processing Stripe payment events. This endpoint receives notifications from Stripe about payment status changes, subscription updates, and other payment-related events. The webhook signature is verified for security."
    )
    public ResponseEntity<String> handleStripeWebhook(
            @Parameter(description = "Raw webhook payload from Stripe", required = true)
            @RequestBody String payload,
            @Parameter(description = "Stripe signature header for webhook verification", required = true)
            @RequestHeader("Stripe-Signature") String sigHeader)
    {
        
        log.info("Received Stripe webhook");
        
        Event event;
        try {
            event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
        } catch (SignatureVerificationException e) {
            log.error("Invalid signature for Stripe webhook", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid signature");
        }
        
        try {
            webhookService.handleStripeEvent(event);
            return ResponseEntity.ok("Webhook handled successfully");
        } catch (Exception e) {
            log.error("Error handling Stripe webhook", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Webhook handling failed");
        }
    }
}

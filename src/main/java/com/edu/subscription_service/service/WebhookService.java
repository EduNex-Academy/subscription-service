package com.edu.subscription_service.service;

import com.edu.subscription_service.entity.Payment;
import com.edu.subscription_service.entity.UserSubscription;
import com.edu.subscription_service.entity.WebhookEvent;
import com.edu.subscription_service.repository.UserSubscriptionRepository;
import com.edu.subscription_service.repository.WebhookEventRepository;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Subscription;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebhookService {
    
    private final WebhookEventRepository webhookEventRepository;
    private final UserSubscriptionRepository subscriptionRepository;
    private final StripeService stripeService;
    private final SubscriptionService subscriptionService;
    
    @Transactional
    public void handleStripeEvent(Event event) {
        log.info("Processing Stripe event: {} with ID: {}", event.getType(), event.getId());
        
        // Check if event already processed
        if (webhookEventRepository.existsByStripeEventId(event.getId())) {
            log.info("Event already processed: {}", event.getId());
            return;
        }
        
        // Save webhook event
        WebhookEvent webhookEvent = new WebhookEvent();
        webhookEvent.setStripeEventId(event.getId());
        webhookEvent.setEventType(event.getType());
        webhookEvent.setProcessed(false);
        webhookEvent.setProcessingAttempts(1);
        webhookEventRepository.save(webhookEvent);
        
        try {
            switch (event.getType()) {
                case "payment_intent.succeeded":
                    handlePaymentIntentSucceeded(event);
                    break;
                case "payment_intent.payment_failed":
                    handlePaymentIntentFailed(event);
                    break;
                case "invoice.payment_succeeded":
                    handleInvoicePaymentSucceeded(event);
                    break;
                case "invoice.payment_failed":
                    handleInvoicePaymentFailed(event);
                    break;
                case "customer.subscription.created":
                    handleSubscriptionCreated(event);
                    break;
                case "customer.subscription.updated":
                    handleSubscriptionUpdated(event);
                    break;
                case "customer.subscription.deleted":
                    handleSubscriptionDeleted(event);
                    break;
                default:
                    log.info("Unhandled event type: {}", event.getType());
            }
            
            // Mark as processed
            webhookEvent.setProcessed(true);
            webhookEvent.setProcessedAt(LocalDateTime.now());
            webhookEventRepository.save(webhookEvent);
            
        } catch (Exception e) {
            log.error("Error processing webhook event: {}", event.getId(), e);
            webhookEvent.setLastProcessingError(e.getMessage());
            webhookEvent.setProcessingAttempts(webhookEvent.getProcessingAttempts() + 1);
            webhookEventRepository.save(webhookEvent);
            throw e;
        }
    }
    
    private void handlePaymentIntentSucceeded(Event event) {
        log.info("Payment intent succeeded");

        // Get PaymentIntent from event using a simpler approach
        PaymentIntent paymentIntent = null;
        try {
            // Try the standard deserializer approach first
            if (event.getDataObjectDeserializer().getObject().isPresent()) {
                Object obj = event.getDataObjectDeserializer().getObject().get();
                if (obj instanceof PaymentIntent) {
                    paymentIntent = (PaymentIntent) obj;
                    log.info("Successfully deserialized PaymentIntent using standard approach");
                }
            }
        } catch (Exception e) {
            log.warn("Standard deserialization failed, will try alternative approach", e);
        }

        // If deserialization failed, we'll need the payment intent ID from elsewhere
        if (paymentIntent == null) {
            log.warn("PaymentIntent deserialization failed. Event type: {}, Event ID: {}",
                    event.getType(), event.getId());
            log.warn("This may indicate an issue with Stripe webhook configuration or library version");
            return;
        }

        log.info("Payment succeeded: {}", paymentIntent.getId());
        log.debug("PaymentIntent metadata: {}", paymentIntent.getMetadata());

        // Update payment status
        try {
            stripeService.updatePaymentStatus(paymentIntent.getId(), Payment.PaymentStatus.COMPLETED, null);
            log.info("Updated payment status to COMPLETED for payment intent: {}", paymentIntent.getId());
        } catch (Exception e) {
            log.error("Failed to update payment status", e);
        }

        // If this is for a subscription, activate it
        String userId = paymentIntent.getMetadata().get("userId");
        log.info("UserId from metadata: {}", userId);

        if (userId == null) {
            log.warn("No userId found in PaymentIntent metadata, skipping subscription activation");
            return;
        }

        try {
            UUID userUuid = UUID.fromString(userId);
            log.info("Looking for active subscription for user: {}", userUuid);

            subscriptionRepository.findActiveByUserId(userUuid)
                    .ifPresentOrElse(subscription -> {
                        log.info("Found subscription: {} with status: {}", subscription.getId(), subscription.getStatus());
                        if (subscription.getStatus() == UserSubscription.SubscriptionStatus.PENDING) {
                            log.info("Activating subscription: {}", subscription.getId());
                            try {
                                subscriptionService.activateSubscription(subscription.getId());
                                log.info("Successfully activated subscription: {}", subscription.getId());
                            } catch (Exception e) {
                                log.error("Failed to activate subscription: {}", subscription.getId(), e);
                            }
                        } else {
                            log.info("Subscription status is not PENDING, current status: {}", subscription.getStatus());
                        }
                    }, () -> log.warn("No active subscription found for user: {}", userUuid));
        } catch (IllegalArgumentException e) {
            log.error("Invalid UUID format for userId: {}", userId, e);
        } catch (Exception e) {
            log.error("Error processing subscription activation for user: {}", userId, e);
        }
    }
    
    private void handlePaymentIntentFailed(Event event) {
        log.info("Payment intent failed");
        PaymentIntent paymentIntent = (PaymentIntent) event.getDataObjectDeserializer().getObject().orElse(null);
        if (paymentIntent != null) {
            log.info("Payment failed: {}", paymentIntent.getId());
            String failureReason = paymentIntent.getLastPaymentError() != null 
                ? paymentIntent.getLastPaymentError().getMessage() 
                : "Payment failed";
            stripeService.updatePaymentStatus(paymentIntent.getId(), Payment.PaymentStatus.FAILED, failureReason);
        }
    }
    
    private void handleInvoicePaymentSucceeded(Event event) {
        log.info("Invoice payment succeeded");
        // Handle recurring subscription payments
    }
    
    private void handleInvoicePaymentFailed(Event event) {
        log.info("Invoice payment failed");
        // Handle failed recurring payments
    }
    
    private void handleSubscriptionCreated(Event event) {
        log.info("Subscription created");
        Subscription subscription = (Subscription) event.getDataObjectDeserializer().getObject().orElse(null);
        if (subscription != null) {
            log.info("Subscription created in Stripe: {}", subscription.getId());
        }
    }
    
    private void handleSubscriptionUpdated(Event event) {
        log.info("Subscription updated");
        Subscription subscription = (Subscription) event.getDataObjectDeserializer().getObject().orElse(null);
        if (subscription != null) {
            log.info("Subscription updated in Stripe: {}", subscription.getId());
            // Update subscription status based on Stripe status
            updateSubscriptionFromStripe(subscription);
        }
    }
    
    private void handleSubscriptionDeleted(Event event) {
        log.info("Subscription deleted");
        Subscription subscription = (Subscription) event.getDataObjectDeserializer().getObject().orElse(null);
        if (subscription != null) {
            log.info("Subscription cancelled in Stripe: {}", subscription.getId());
            subscriptionRepository.findByStripeSubscriptionId(subscription.getId())
                    .ifPresent(userSub -> {
                        userSub.setStatus(UserSubscription.SubscriptionStatus.CANCELLED);
                        subscriptionRepository.save(userSub);
                    });
        }
    }
    
    private void updateSubscriptionFromStripe(Subscription stripeSubscription) {
        subscriptionRepository.findByStripeSubscriptionId(stripeSubscription.getId())
                .ifPresent(userSubscription -> {
                    UserSubscription.SubscriptionStatus newStatus = mapStripeStatusToLocal(stripeSubscription.getStatus());
                    if (newStatus != userSubscription.getStatus()) {
                        userSubscription.setStatus(newStatus);
                        subscriptionRepository.save(userSubscription);
                        log.info("Updated subscription status to: {}", newStatus);
                    }
                });
    }
    
    private UserSubscription.SubscriptionStatus mapStripeStatusToLocal(String stripeStatus) {
        return switch (stripeStatus) {
            case "active" -> UserSubscription.SubscriptionStatus.ACTIVE;
            case "canceled" -> UserSubscription.SubscriptionStatus.CANCELLED;
            case "incomplete", "incomplete_expired" -> UserSubscription.SubscriptionStatus.PENDING;
            case "past_due", "unpaid" -> UserSubscription.SubscriptionStatus.EXPIRED;
            default -> UserSubscription.SubscriptionStatus.PENDING;
        };
    }
}

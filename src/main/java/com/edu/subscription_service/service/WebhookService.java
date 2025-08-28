package com.edu.subscription_service.service;

import com.edu.subscription_service.entity.Payment;
import com.edu.subscription_service.entity.UserSubscription;
import com.edu.subscription_service.repository.UserSubscriptionRepository;
import com.stripe.model.Event;
import com.stripe.model.Invoice;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Subscription;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebhookService {
    
    private final UserSubscriptionRepository subscriptionRepository;
    private final StripeService stripeService;
    private final PointsService pointsService;
    
    @Transactional
    public void handleStripeEvent(Event event) {
        log.info("Processing Stripe event: {} with ID: {}", event.getType(), event.getId());
        
        try {
            switch (event.getType()) {
                // Customer events
                case "customer.created":
                    handleCustomerCreated(event);
                    break;
                case "customer.updated":
                    handleCustomerUpdated(event);
                    break;
                    
                // Payment events
                case "payment_intent.succeeded":
                    handlePaymentIntentSucceeded(event);
                    break;
                case "payment_intent.payment_failed":
                    handlePaymentIntentFailed(event);
                    break;
                    
                // Invoice events (for renewals)
                case "invoice.payment_succeeded":
                    handleInvoicePaymentSucceeded(event);
                    break;
                case "invoice.payment_failed":
                    handleInvoicePaymentFailed(event);
                    break;
                case "invoice.created":
                    handleInvoiceCreated(event);
                    break;
                    
                // Subscription events - MOST IMPORTANT for Stripe-first approach
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
                    log.info("Unhandled event type: {} - logging for future reference", event.getType());
            }
            
            log.info("Successfully processed Stripe event: {}", event.getId());
            
        } catch (Exception e) {
            log.error("Error processing webhook event: {}", event.getId(), e);
            throw e;
        }
    }
    
    private void handleCustomerCreated(Event event) {
        log.info("Customer created in Stripe - logging event");
        // Just log the customer creation, don't create anything in our DB yet
    }
    
    private void handleCustomerUpdated(Event event) {
        log.info("Customer updated in Stripe - logging event");
        // Log customer updates
    }
    
    private void handleInvoiceCreated(Event event) {
        log.info("Invoice created in Stripe - logging for billing tracking");
        // Log invoice creation for billing history
    }
    
    private void handlePaymentIntentSucceeded(Event event) {
        log.info("Payment intent succeeded");

        try {
            // Safely get PaymentIntent from event using modern approach
            PaymentIntent paymentIntent = null;
            if (event.getDataObjectDeserializer().getObject().isPresent()) {
                Object obj = event.getDataObjectDeserializer().getObject().get();
                if (obj instanceof PaymentIntent) {
                    paymentIntent = (PaymentIntent) obj;
                }
            }
            
            if (paymentIntent == null) {
                // Fallback: retrieve from Stripe API with error handling
                String paymentIntentId = extractPaymentIntentId(event);
                if (paymentIntentId != null) {
                    try {
                        paymentIntent = stripeService.getPaymentIntent(paymentIntentId);
                    } catch (Exception e) {
                        log.warn("Could not retrieve PaymentIntent {} from Stripe API: {}", paymentIntentId, e.getMessage());
                        // Continue without PaymentIntent - we can still log the event
                    }
                }
            }
            
            if (paymentIntent == null) {
                log.warn("Failed to get PaymentIntent from event: {} - logging event anyway", event.getId());
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

            // Handle subscription activation
            handleSubscriptionActivation(paymentIntent);

        } catch (Exception e) {
            log.error("Error handling payment intent succeeded event: {}", event.getId(), e);
        }
    }
    
    private void handleSubscriptionActivation(PaymentIntent paymentIntent) {
        String userId = paymentIntent.getMetadata().get("userId");
        log.info("UserId from metadata: {}", userId);

        if (userId == null) {
            log.warn("No userId found in PaymentIntent metadata, checking for subscriptions by customer");
            return;
        }

        try {
            UUID userUuid = UUID.fromString(userId);
            log.info("Looking for pending subscription for user: {}", userUuid);

            subscriptionRepository.findPendingByUserId(userUuid)
                    .ifPresentOrElse(subscription -> {
                        log.info("Found pending subscription: {} with status: {}", subscription.getId(), subscription.getStatus());
                        try {
                            activateUserSubscription(subscription);
                            log.info("Successfully activated subscription: {}", subscription.getId());
                        } catch (Exception e) {
                            log.error("Failed to activate subscription: {}", subscription.getId(), e);
                        }
                    }, () -> log.warn("No pending subscription found for user: {}", userUuid));
        } catch (IllegalArgumentException e) {
            log.error("Invalid UUID format for userId: {}", userId, e);
        } catch (Exception e) {
            log.error("Error processing subscription activation for user: {}", userId, e);
        }
    }
    
    private void activateUserSubscription(UserSubscription subscription) {
        subscription.setStatus(UserSubscription.SubscriptionStatus.ACTIVE);
        subscription.setStartDate(LocalDateTime.now());
        subscriptionRepository.save(subscription);
        log.info("Updated subscription status to ACTIVE for subscription: {}", subscription.getId());

        // Award points for subscription
        try {
            pointsService.awardPoints(
                    subscription.getUserId(),
                    subscription.getPlan().getPointsAwarded(),
                    "Subscribed to " + subscription.getPlan().getName() + " plan",
                    "SUBSCRIPTION",
                    subscription.getId()
            );
            log.info("Points awarded successfully for subscription: {}", subscription.getId());
        } catch (Exception e) {
            log.error("Failed to award points for subscription: {}", subscription.getId(), e);
        }
    }
    
    private void handlePaymentIntentFailed(Event event) {
        log.info("Payment intent failed");
        
        try {
            PaymentIntent paymentIntent = null;
            if (event.getDataObjectDeserializer().getObject().isPresent()) {
                Object obj = event.getDataObjectDeserializer().getObject().get();
                if (obj instanceof PaymentIntent) {
                    paymentIntent = (PaymentIntent) obj;
                }
            }
            
            if (paymentIntent == null) {
                String paymentIntentId = extractPaymentIntentId(event);
                if (paymentIntentId != null) {
                    paymentIntent = stripeService.getPaymentIntent(paymentIntentId);
                }
            }
            
            if (paymentIntent != null) {
                log.info("Payment failed: {}", paymentIntent.getId());
                String failureReason = paymentIntent.getLastPaymentError() != null 
                    ? paymentIntent.getLastPaymentError().getMessage() 
                    : "Payment failed";
                stripeService.updatePaymentStatus(paymentIntent.getId(), Payment.PaymentStatus.FAILED, failureReason);
                
                // Mark related subscription as failed if it's still pending
                String userId = paymentIntent.getMetadata().get("userId");
                if (userId != null) {
                    try {
                        UUID userUuid = UUID.fromString(userId);
                        subscriptionRepository.findPendingByUserId(userUuid)
                                .ifPresent(subscription -> {
                                    subscription.setStatus(UserSubscription.SubscriptionStatus.CANCELLED);
                                    subscriptionRepository.save(subscription);
                                    log.info("Marked subscription as cancelled due to payment failure: {}", subscription.getId());
                                });
                    } catch (Exception e) {
                        log.error("Error handling subscription for failed payment: {}", userId, e);
                    }
                }
            } else {
                log.error("Failed to get PaymentIntent from failed payment event: {}", event.getId());
            }
        } catch (Exception e) {
            log.error("Error handling payment intent failed event: {}", event.getId(), e);
        }
    }
    
    private void handleInvoicePaymentSucceeded(Event event) {
        log.info("Invoice payment succeeded - handling recurring payment");
        
        try {
            Invoice invoice = null;
            if (event.getDataObjectDeserializer().getObject().isPresent()) {
                Object obj = event.getDataObjectDeserializer().getObject().get();
                if (obj instanceof Invoice) {
                    invoice = (Invoice) obj;
                }
            }
            
            if (invoice != null && invoice.getSubscription() != null) {
                log.info("Processing successful recurring payment for subscription: {}", invoice.getSubscription());
                
                // Find and update subscription
                subscriptionRepository.findByStripeSubscriptionId(invoice.getSubscription())
                        .ifPresent(userSubscription -> {
                            if (userSubscription.getStatus() != UserSubscription.SubscriptionStatus.ACTIVE) {
                                userSubscription.setStatus(UserSubscription.SubscriptionStatus.ACTIVE);
                                
                                // Extend end date based on billing cycle
                                LocalDateTime newEndDate = calculateNewEndDate(userSubscription);
                                userSubscription.setEndDate(newEndDate);
                                
                                subscriptionRepository.save(userSubscription);
                                log.info("Renewed subscription: {} until {}", userSubscription.getId(), newEndDate);
                                
                                // Award points for renewal
                                try {
                                    pointsService.awardPoints(
                                            userSubscription.getUserId(),
                                            userSubscription.getPlan().getPointsAwarded(),
                                            "Subscription renewed for " + userSubscription.getPlan().getName() + " plan",
                                            "SUBSCRIPTION_RENEWAL",
                                            userSubscription.getId()
                                    );
                                    log.info("Points awarded for subscription renewal: {}", userSubscription.getId());
                                } catch (Exception e) {
                                    log.error("Failed to award points for subscription renewal: {}", userSubscription.getId(), e);
                                }
                            }
                        });
            }
        } catch (Exception e) {
            log.error("Error handling invoice payment succeeded event: {}", event.getId(), e);
        }
    }
    
    private void handleInvoicePaymentFailed(Event event) {
        log.info("Invoice payment failed - handling failed recurring payment");
        
        try {
            Invoice invoice = null;
            if (event.getDataObjectDeserializer().getObject().isPresent()) {
                Object obj = event.getDataObjectDeserializer().getObject().get();
                if (obj instanceof Invoice) {
                    invoice = (Invoice) obj;
                }
            }
            
            if (invoice != null && invoice.getSubscription() != null) {
                log.info("Processing failed recurring payment for subscription: {}", invoice.getSubscription());
                
                subscriptionRepository.findByStripeSubscriptionId(invoice.getSubscription())
                        .ifPresent(userSubscription -> {
                            // Mark as past due but don't cancel immediately
                            userSubscription.setStatus(UserSubscription.SubscriptionStatus.EXPIRED);
                            subscriptionRepository.save(userSubscription);
                            log.info("Marked subscription as past due: {}", userSubscription.getId());
                        });
            }
        } catch (Exception e) {
            log.error("Error handling invoice payment failed event: {}", event.getId(), e);
        }
    }
    
    private void handleSubscriptionCreated(Event event) {
        log.info("=== STRIPE SUBSCRIPTION CREATED ===");
        
        try {
            // Use the proper way to get Stripe objects from events
            Subscription subscription = getStripeSubscriptionFromEvent(event);
            
            if (subscription != null) {
                log.info("New Stripe subscription created:");
                logStripeSubscriptionDetails(subscription, "CREATED");
                
                // Mirror this in our database as a log/record
                syncSubscriptionToDatabase(subscription, "CREATED_FROM_STRIPE");
                
                // If subscription is active immediately, award points
                if ("active".equals(subscription.getStatus())) {
                    awardPointsForSubscription(subscription, "NEW_SUBSCRIPTION");
                }
                
            } else {
                log.error("Failed to get Subscription from created event: {}", event.getId());
            }
        } catch (Exception e) {
            log.error("Error handling subscription created event: {}", event.getId(), e);
        }
    }
    
    private void handleSubscriptionUpdated(Event event) {
        log.info("=== STRIPE SUBSCRIPTION UPDATED ===");
        
        try {
            Subscription subscription = getStripeSubscriptionFromEvent(event);
            
            if (subscription != null) {
                log.info("Stripe subscription updated:");
                logStripeSubscriptionDetails(subscription, "UPDATED");
                
                // Sync the updated state to our database
                syncSubscriptionToDatabase(subscription, "UPDATED_FROM_STRIPE");
                
                // Check if subscription became active (payment succeeded)
                if ("active".equals(subscription.getStatus())) {
                    UserSubscription dbSubscription = subscriptionRepository
                            .findByStripeSubscriptionId(subscription.getId())
                            .orElse(null);
                    
                    if (dbSubscription != null && 
                        dbSubscription.getStatus() != UserSubscription.SubscriptionStatus.ACTIVE) {
                        awardPointsForSubscription(subscription, "SUBSCRIPTION_ACTIVATED");
                    }
                }
                
            } else {
                log.error("Failed to get Subscription from updated event: {}", event.getId());
            }
        } catch (Exception e) {
            log.error("Error handling subscription updated event: {}", event.getId(), e);
        }
    }
    
    private void handleSubscriptionDeleted(Event event) {
        log.info("=== STRIPE SUBSCRIPTION CANCELLED ===");
        
        try {
            Subscription subscription = getStripeSubscriptionFromEvent(event);
            
            if (subscription != null) {
                log.info("Stripe subscription cancelled:");
                logStripeSubscriptionDetails(subscription, "CANCELLED");
                
                // Sync cancellation to our database
                syncSubscriptionToDatabase(subscription, "CANCELLED_FROM_STRIPE");
                
            } else {
                log.error("Failed to get Subscription from deleted event: {}", event.getId());
            }
        } catch (Exception e) {
            log.error("Error handling subscription deleted event: {}", event.getId(), e);
        }
    }
    
    private Subscription getStripeSubscriptionFromEvent(Event event) {
        try {
            // Use the modern way to get objects from Stripe events
            if (event.getDataObjectDeserializer().getObject().isPresent()) {
                Object obj = event.getDataObjectDeserializer().getObject().get();
                if (obj instanceof Subscription) {
                    return (Subscription) obj;
                }
            }
            
            log.warn("Could not deserialize Subscription from event using modern approach: {}", event.getId());
            
        } catch (Exception e) {
            log.warn("Could not deserialize Subscription from event: {}", event.getId(), e);
        }
        return null;
    }
    
    private void logStripeSubscriptionDetails(Subscription subscription, String eventType) {
        log.info("📋 Stripe Subscription {} Details:", eventType);
        log.info("   ID: {}", subscription.getId());
        log.info("   Status: {}", subscription.getStatus());
        log.info("   Customer: {}", subscription.getCustomer());
        log.info("   Current Period: {} to {}", 
                subscription.getCurrentPeriodStart() != null ? 
                    java.time.Instant.ofEpochSecond(subscription.getCurrentPeriodStart()) : "null",
                subscription.getCurrentPeriodEnd() != null ? 
                    java.time.Instant.ofEpochSecond(subscription.getCurrentPeriodEnd()) : "null"
        );
        log.info("   Auto Renewal: {}", !subscription.getCancelAtPeriodEnd());
        
        if (subscription.getItems() != null && !subscription.getItems().getData().isEmpty()) {
            log.info("   Price ID: {}", subscription.getItems().getData().get(0).getPrice().getId());
        }
        
        if (subscription.getMetadata() != null) {
            log.info("   User ID: {}", subscription.getMetadata().get("userId"));
        }
    }
    
    private void syncSubscriptionToDatabase(Subscription stripeSubscription, String syncReason) {
        try {
            log.info("🔄 Syncing Stripe subscription to database: {} ({})", 
                    stripeSubscription.getId(), syncReason);
            
            // Find existing record or create new one
            UserSubscription dbSubscription = subscriptionRepository
                    .findByStripeSubscriptionId(stripeSubscription.getId())
                    .orElse(null);
            
            if (dbSubscription == null) {
                // Create new record if it doesn't exist
                String userIdStr = stripeSubscription.getMetadata() != null ? 
                        stripeSubscription.getMetadata().get("userId") : null;
                
                if (userIdStr != null) {
                    UUID userId = UUID.fromString(userIdStr);
                    dbSubscription = createSubscriptionRecord(stripeSubscription, userId);
                } else {
                    log.warn("Cannot create DB record - no userId in Stripe subscription metadata");
                    return;
                }
            }
            
            // Update the database record to match Stripe
            updateSubscriptionFromStripe(dbSubscription, stripeSubscription);
            
        } catch (Exception e) {
            log.error("Error syncing subscription to database: {}", stripeSubscription.getId(), e);
        }
    }
    
    private UserSubscription createSubscriptionRecord(Subscription stripeSubscription, UUID userId) {
        // Note: In a Stripe-first approach, we might not have the plan in our DB yet
        // For now, we'll create a basic record and update it as needed
        UserSubscription subscription = new UserSubscription();
        subscription.setUserId(userId);
        subscription.setStripeSubscriptionId(stripeSubscription.getId());
        subscription.setStripeCustomerId(stripeSubscription.getCustomer());
        subscription.setAutoRenew(!stripeSubscription.getCancelAtPeriodEnd());
        
        // Try to find the plan by Stripe price ID
        if (stripeSubscription.getItems() != null && !stripeSubscription.getItems().getData().isEmpty()) {
            String stripePriceId = stripeSubscription.getItems().getData().get(0).getPrice().getId();
            // You could look up the plan here if needed
            log.info("Subscription uses Stripe price: {}", stripePriceId);
        }
        
        return subscriptionRepository.save(subscription);
    }
    
    private void updateSubscriptionFromStripe(UserSubscription dbSubscription, Subscription stripeSubscription) {
        // Map Stripe status to our local status for logging purposes
        UserSubscription.SubscriptionStatus newStatus = mapStripeStatusToLocal(stripeSubscription.getStatus());
        UserSubscription.SubscriptionStatus oldStatus = dbSubscription.getStatus();
        
        log.info("🔄 Updating DB subscription {} from Stripe: {} → {}", 
                dbSubscription.getId(), oldStatus, newStatus);
        
        dbSubscription.setStatus(newStatus);
        dbSubscription.setAutoRenew(!stripeSubscription.getCancelAtPeriodEnd());
        
        // Update dates based on Stripe subscription
        if (stripeSubscription.getCurrentPeriodStart() != null) {
            dbSubscription.setStartDate(LocalDateTime.ofInstant(
                    Instant.ofEpochSecond(stripeSubscription.getCurrentPeriodStart()), 
                    ZoneId.systemDefault()));
        }
        
        if (stripeSubscription.getCurrentPeriodEnd() != null) {
            dbSubscription.setEndDate(LocalDateTime.ofInstant(
                    Instant.ofEpochSecond(stripeSubscription.getCurrentPeriodEnd()), 
                    ZoneId.systemDefault()));
        }
        
        subscriptionRepository.save(dbSubscription);
        log.info("✅ Database subscription updated successfully");
    }
    
    private void awardPointsForSubscription(Subscription stripeSubscription, String reason) {
        try {
            String userIdStr = stripeSubscription.getMetadata() != null ? 
                    stripeSubscription.getMetadata().get("userId") : null;
            
            if (userIdStr != null) {
                UUID userId = UUID.fromString(userIdStr);
                
                // Find the subscription plan to get points
                UserSubscription dbSubscription = subscriptionRepository
                        .findByStripeSubscriptionId(stripeSubscription.getId())
                        .orElse(null);
                
                if (dbSubscription != null && dbSubscription.getPlan() != null) {
                    log.info("🎁 Awarding {} points to user {} for: {}", 
                            dbSubscription.getPlan().getPointsAwarded(), userId, reason);
                    
                    pointsService.awardPoints(
                            userId,
                            dbSubscription.getPlan().getPointsAwarded(),
                            reason + " - " + dbSubscription.getPlan().getName(),
                            "SUBSCRIPTION",
                            dbSubscription.getId()
                    );
                    
                    log.info("✅ Points awarded successfully");
                } else {
                    log.warn("Cannot award points - subscription plan not found in database");
                }
            } else {
                log.warn("Cannot award points - no userId in Stripe subscription metadata");
            }
        } catch (Exception e) {
            log.error("Error awarding points for subscription: {}", stripeSubscription.getId(), e);
        }
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
    
    private LocalDateTime calculateNewEndDate(UserSubscription subscription) {
        return switch (subscription.getPlan().getBillingCycle()) {
            case MONTHLY -> subscription.getEndDate().plusMonths(1);
            case YEARLY -> subscription.getEndDate().plusYears(1);
        };
    }
    
    private String extractPaymentIntentId(Event event) {
        try {
            if (event.getId() != null && event.getId().startsWith("evt_")) {
                // Extract from event ID - format is usually evt_XXXXoriginalIdXXXX
                String eventId = event.getId();
                if (eventId.contains("_")) {
                    String[] parts = eventId.split("_");
                    if (parts.length > 1 && parts[1].startsWith("3")) {
                        return "pi_" + parts[1];
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Could not extract payment intent ID from event: {}", event.getId(), e);
        }
        return null;
    }
}

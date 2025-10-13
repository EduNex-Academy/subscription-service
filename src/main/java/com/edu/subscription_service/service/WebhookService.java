package com.edu.subscription_service.service;

import com.edu.subscription_service.entity.UserSubscription;
import com.edu.subscription_service.entity.SubscriptionPlan;
import com.edu.subscription_service.repository.UserSubscriptionRepository;
import com.stripe.model.Event;
import com.stripe.model.Invoice;
import com.stripe.model.Subscription;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebhookService {
    
    private final UserSubscriptionRepository subscriptionRepository;
    private final PointsService pointsService;
    private final InstructorPayoutService instructorPayoutService;
    private final SubscriptionPlanService subscriptionPlanService;
    
    @Transactional
    public void handleStripeEvent(Event event) {
        log.info("🔄 [WEBHOOK-V2] Processing Stripe event: {} with ID: {}", event.getType(), event.getId());
        
        try {
            switch (event.getType()) {
                case "customer.subscription.created":
                    log.info("🎯 [WEBHOOK-V2] Handling subscription created");
                    handleSubscriptionCreated(event);
                    break;
                    
                case "customer.subscription.updated":
                    log.info("🎯 [WEBHOOK-V2] Handling subscription updated");
                    handleSubscriptionUpdated(event);
                    break;

                case "customer.subscription.deleted":
                    log.info("🎯 [WEBHOOK-V2] Handling subscription deleted");
                    handleSubscriptionDeleted(event);
                    break;
                    
                case "invoice.payment_succeeded":
                    log.info("🎯 [WEBHOOK-V2] Handling invoice payment succeeded");
                    handleInvoicePaymentSucceeded(event);
                    break;

                case "invoice.payment_failed":
                    log.info("🎯 [WEBHOOK-V2] Handling invoice payment failed");
                    handleInvoicePaymentFailed(event);
                    break;

                case "invoice_payment.paid":
                    log.info("🎯 [WEBHOOK-V2] Handling invoice_payment.paid - THIS IS THE NEW HANDLER!");
                    handleInvoicePaymentPaid(event);
                    break;
                    
                case "setup_intent.created":
                    log.info("🎯 [WEBHOOK-V2] Handling setup intent created");
                    handleSetupIntentCreated(event);
                    break;
                    
                case "setup_intent.succeeded":
                    log.info("🎯 [WEBHOOK-V2] Handling setup intent succeeded");
                    handleSetupIntentSucceeded(event);
                    break;
                    
                case "payment_method.attached":
                    log.info("🎯 [WEBHOOK-V2] Handling payment method attached");
                    handlePaymentMethodAttached(event);
                    break;
                    
                case "charge.succeeded":
                    log.info("🎯 [WEBHOOK-V2] Handling charge succeeded");
                    handleChargeSucceeded(event);
                    break;
                    
                case "payment_intent.succeeded":
                    log.info("🎯 [WEBHOOK-V2] Handling payment intent succeeded");
                    handlePaymentIntentSucceeded(event);
                    break;
                    
                case "payment_intent.created":
                    log.info("🎯 [WEBHOOK-V2] Handling payment intent created");
                    handlePaymentIntentCreated(event);
                    break;
                    
                case "invoice.created":
                    log.info("🎯 [WEBHOOK-V2] Handling invoice created");
                    handleInvoiceCreated(event);
                    break;
                    
                case "invoice.finalized":
                    log.info("🎯 [WEBHOOK-V2] Handling invoice finalized");
                    handleInvoiceFinalized(event);
                    break;
                    
                case "invoice.paid":
                    log.info("🎯 [WEBHOOK-V2] Handling invoice paid");
                    handleInvoicePaid(event);
                    break;

                default:
                    log.debug("⚠️ [WEBHOOK-V2] Unhandled event type: {} - This event is not critical for subscription processing", event.getType());
            }
            
            log.info("✅ [WEBHOOK-V2] Successfully processed Stripe event: {} ({})", event.getType(), event.getId());
            
        } catch (Exception e) {
            log.error("❌ [WEBHOOK-V2] Error processing webhook event: {} ({})", event.getType(), event.getId(), e);
            throw e;
        }
    }
    
    private void handleSubscriptionCreated(Event event) {
        log.info("=== SUBSCRIPTION CREATED ===");

        Subscription subscription = extractSubscription(event);
        if (subscription == null) return;

        logSubscriptionDetails(subscription);
        
        // Before creating new subscription, check if user already has active subscriptions
        String userIdStr = subscription.getMetadata() != null ?
                subscription.getMetadata().get("userId") : null;
        
        if (userIdStr != null) {
            UUID userId = UUID.fromString(userIdStr);
            
            // Cancel any other active subscriptions for this user to prevent duplicates
            List<UserSubscription> activeSubscriptions = subscriptionRepository
                    .findByUserIdAndStatus(userId, UserSubscription.SubscriptionStatus.ACTIVE);
            
            for (UserSubscription activeSub : activeSubscriptions) {
                if (!subscription.getId().equals(activeSub.getStripeSubscriptionId())) {
                    log.info("⚠️ Found existing active subscription {}, marking as cancelled to prevent duplicates", 
                             activeSub.getStripeSubscriptionId());
                    activeSub.setStatus(UserSubscription.SubscriptionStatus.CANCELLED);
                    activeSub.setAutoRenew(false);
                    subscriptionRepository.save(activeSub);
                }
            }
        }
        
        syncSubscriptionToDatabase(subscription);

        // Award points if subscription is active immediately
        if ("active".equals(subscription.getStatus())) {
            awardPointsForNewSubscription(subscription);
        }
    }

    private void handleSubscriptionUpdated(Event event) {
        log.info("=== SUBSCRIPTION UPDATED ===");

        Subscription subscription = extractSubscription(event);
        if (subscription == null) return;

        logSubscriptionDetails(subscription);

        UserSubscription dbSubscription = subscriptionRepository
                .findByStripeSubscriptionId(subscription.getId())
                .orElse(null);

        if (dbSubscription == null) {
            log.warn("DB subscription not found, creating new record");
            syncSubscriptionToDatabase(subscription);
            return;
        }

        UserSubscription.SubscriptionStatus oldStatus = dbSubscription.getStatus();
        updateSubscriptionFromStripe(dbSubscription, subscription);

        // Award points when subscription becomes active for the first time
        if ("active".equals(subscription.getStatus()) &&
            oldStatus != UserSubscription.SubscriptionStatus.ACTIVE) {
            awardPointsForNewSubscription(subscription);
        }
    }
    
    private void handleSubscriptionDeleted(Event event) {
        log.info("=== SUBSCRIPTION DELETED ===");

        Subscription subscription = extractSubscription(event);
        if (subscription == null) return;

        subscriptionRepository.findByStripeSubscriptionId(subscription.getId())
                .ifPresent(dbSubscription -> {
                    dbSubscription.setStatus(UserSubscription.SubscriptionStatus.CANCELLED);
                    dbSubscription.setAutoRenew(false);
                    subscriptionRepository.save(dbSubscription);
                    log.info("Cancelled subscription: {}", dbSubscription.getId());
                    
                    // Additional cleanup: cancel any other PENDING subscriptions for the same user
                    // to prevent duplicate active subscriptions when user creates a new one
                    UUID userId = dbSubscription.getUserId();
                    List<UserSubscription> pendingSubscriptions = subscriptionRepository
                            .findByUserIdAndStatus(userId, UserSubscription.SubscriptionStatus.PENDING);
                    
                    for (UserSubscription pendingSub : pendingSubscriptions) {
                        if (!pendingSub.getId().equals(dbSubscription.getId())) {
                            pendingSub.setStatus(UserSubscription.SubscriptionStatus.CANCELLED);
                            subscriptionRepository.save(pendingSub);
                            log.info("Cleaned up pending subscription: {}", pendingSub.getId());
                        }
                    }
                });
    }
    
    private void handleInvoicePaymentSucceeded(Event event) {
        log.info("=== INVOICE PAYMENT SUCCEEDED (Renewal) ===");

        Invoice invoice = extractInvoice(event);
        if (invoice == null || invoice.getSubscription() == null) return;

        String subscriptionId = invoice.getSubscription();
        log.info("Processing renewal payment for subscription: {}", subscriptionId);

        subscriptionRepository.findByStripeSubscriptionId(subscriptionId)
                .ifPresent(dbSubscription -> {
                    // Stripe automatically handles the renewal dates
                    // We just need to update our DB and award points

                    try {
                        // Fetch latest subscription data from Stripe
                        Subscription stripeSubscription = Subscription.retrieve(subscriptionId);
                        updateSubscriptionFromStripe(dbSubscription, stripeSubscription);

                        // Award points for renewal
                        pointsService.awardPoints(
                                dbSubscription.getUserId(),
                                dbSubscription.getPlan().getPointsAwarded(),
                                "Subscription renewed: " + dbSubscription.getPlan().getName(),
                                "SUBSCRIPTION_RENEWAL",
                                dbSubscription.getId()
                        );

                        // Record instructor revenue share
                        if (invoice.getAmountPaid() != null && invoice.getAmountPaid() > 0) {
                            BigDecimal subscriptionRevenue = new BigDecimal(invoice.getAmountPaid()).divide(new BigDecimal(100)); // Convert cents to dollars
                            instructorPayoutService.recordSubscriptionRevenueShare(dbSubscription.getId(), subscriptionRevenue);
                        }

                        log.info("✅ Subscription renewed, points awarded, and instructor earnings recorded: {}", dbSubscription.getId());

                    } catch (Exception e) {
                        log.error("Error processing renewal for subscription: {}", subscriptionId, e);
                    }
                });
    }
    
    private void handleInvoicePaymentFailed(Event event) {
        log.info("=== INVOICE PAYMENT FAILED ===");

        Invoice invoice = extractInvoice(event);
        if (invoice == null || invoice.getSubscription() == null) return;

        subscriptionRepository.findByStripeSubscriptionId(invoice.getSubscription())
                .ifPresent(dbSubscription -> {
                    dbSubscription.setStatus(UserSubscription.SubscriptionStatus.EXPIRED);
                    subscriptionRepository.save(dbSubscription);
                    log.info("Marked subscription as expired due to payment failure: {}", dbSubscription.getId());
                });
    }

    private void handleInvoicePaymentPaid(Event event) {
        log.info("=== INVOICE PAYMENT PAID ===");

        Invoice invoice = extractInvoice(event);
        if (invoice == null || invoice.getSubscription() == null) return;

        String subscriptionId = invoice.getSubscription();
        log.info("Processing payment confirmation for subscription: {}", subscriptionId);

        subscriptionRepository.findByStripeSubscriptionId(subscriptionId)
                .ifPresent(dbSubscription -> {
                    try {
                        // Fetch latest subscription data from Stripe
                        Subscription stripeSubscription = Subscription.retrieve(subscriptionId);
                        updateSubscriptionFromStripe(dbSubscription, stripeSubscription);

                        // Check if this is the first payment for this subscription
                        boolean isFirstPayment = dbSubscription.getStatus() != UserSubscription.SubscriptionStatus.ACTIVE;

                        // Award points for payment
                        if (dbSubscription.getPlan() != null) {
                            String reason = isFirstPayment ? 
                                "New subscription: " + dbSubscription.getPlan().getName() :
                                "Subscription payment confirmed: " + dbSubscription.getPlan().getName();
                                
                            pointsService.awardPoints(
                                    dbSubscription.getUserId(),
                                    dbSubscription.getPlan().getPointsAwarded(),
                                    reason,
                                    isFirstPayment ? "SUBSCRIPTION" : "SUBSCRIPTION_PAYMENT",
                                    dbSubscription.getId()
                            );
                        }

                        // Record instructor revenue share
                        if (invoice.getAmountPaid() != null && invoice.getAmountPaid() > 0) {
                            BigDecimal subscriptionRevenue = new BigDecimal(invoice.getAmountPaid()).divide(new BigDecimal(100)); // Convert cents to dollars
                            instructorPayoutService.recordSubscriptionRevenueShare(dbSubscription.getId(), subscriptionRevenue);
                        }

                        log.info("✅ Invoice payment processed, points awarded, and instructor earnings recorded: {}", dbSubscription.getId());

                    } catch (Exception e) {
                        log.error("Error processing payment confirmation for subscription: {}", subscriptionId, e);
                    }
                });
    }

    private void handleSetupIntentCreated(Event event) {
        log.info("=== SETUP INTENT CREATED ===");
        log.debug("Setup intent created - no action required, just tracking");
    }

    private void handleSetupIntentSucceeded(Event event) {
        log.info("=== SETUP INTENT SUCCEEDED ===");
        log.debug("Setup intent succeeded - payment method ready for use");
    }

    private void handlePaymentMethodAttached(Event event) {
        log.info("=== PAYMENT METHOD ATTACHED ===");
        log.debug("Payment method attached to customer - ready for subscriptions");
    }

    private void handleChargeSucceeded(Event event) {
        log.info("=== CHARGE SUCCEEDED ===");
        log.debug("Charge succeeded - payment processed successfully");
    }

    private void handlePaymentIntentSucceeded(Event event) {
        log.info("=== PAYMENT INTENT SUCCEEDED ===");
        log.debug("Payment intent succeeded - payment confirmed");
    }

    private void handlePaymentIntentCreated(Event event) {
        log.info("=== PAYMENT INTENT CREATED ===");
        log.debug("Payment intent created - payment process initiated");
    }

    private void handleInvoiceCreated(Event event) {
        log.info("=== INVOICE CREATED ===");
        log.debug("Invoice created for subscription billing");
    }

    private void handleInvoiceFinalized(Event event) {
        log.info("=== INVOICE FINALIZED ===");
        log.debug("Invoice finalized and ready for payment");
    }

    private void handleInvoicePaid(Event event) {
        log.info("=== INVOICE PAID ===");
        
        Invoice invoice = extractInvoice(event);
        if (invoice == null || invoice.getSubscription() == null) {
            log.debug("Invoice paid but no subscription associated");
            return;
        }

        String subscriptionId = invoice.getSubscription();
        log.info("Processing invoice payment for subscription: {}", subscriptionId);

        subscriptionRepository.findByStripeSubscriptionId(subscriptionId)
                .ifPresent(dbSubscription -> {
                    try {
                        // Fetch latest subscription data from Stripe
                        Subscription stripeSubscription = Subscription.retrieve(subscriptionId);
                        updateSubscriptionFromStripe(dbSubscription, stripeSubscription);

                        // Award points for payment
                        if (dbSubscription.getPlan() != null) {
                            pointsService.awardPoints(
                                    dbSubscription.getUserId(),
                                    dbSubscription.getPlan().getPointsAwarded(),
                                    "Invoice paid: " + dbSubscription.getPlan().getName(),
                                    "INVOICE_PAYMENT",
                                    dbSubscription.getId()
                            );
                        }

                        // Record instructor revenue share
                        if (invoice.getAmountPaid() != null && invoice.getAmountPaid() > 0) {
                            BigDecimal subscriptionRevenue = new BigDecimal(invoice.getAmountPaid()).divide(new BigDecimal(100)); // Convert cents to dollars
                            instructorPayoutService.recordSubscriptionRevenueShare(dbSubscription.getId(), subscriptionRevenue);
                        }

                        log.info("✅ Invoice payment processed successfully: {}", dbSubscription.getId());

                    } catch (Exception e) {
                        log.error("Error processing invoice payment for subscription: {}", subscriptionId, e);
                    }
                });
    }

    private Subscription extractSubscription(Event event) {
        try {
            log.info("🔍 Extracting subscription from event: {}", event.getId());
            
            // Get the raw JSON data from the event
            JsonNode eventData = new ObjectMapper().readTree(event.toJson());
            JsonNode dataObject = eventData.get("data").get("object");
            
            if (dataObject == null) {
                log.error("❌ No data.object found in event {}", event.getId());
                return null;
            }
            
            // Create a subscription object from the JSON data
            Subscription subscription = new Subscription();
            
            // Extract basic fields
            if (dataObject.has("id")) {
                subscription.setId(dataObject.get("id").asText());
            }
            if (dataObject.has("status")) {
                subscription.setStatus(dataObject.get("status").asText());
            }
            if (dataObject.has("customer")) {
                subscription.setCustomer(dataObject.get("customer").asText());
            }
            if (dataObject.has("cancel_at_period_end")) {
                subscription.setCancelAtPeriodEnd(dataObject.get("cancel_at_period_end").asBoolean());
            }
            if (dataObject.has("current_period_start")) {
                subscription.setCurrentPeriodStart(dataObject.get("current_period_start").asLong());
            }
            if (dataObject.has("current_period_end")) {
                subscription.setCurrentPeriodEnd(dataObject.get("current_period_end").asLong());
            }
            
            // Extract metadata
            if (dataObject.has("metadata")) {
                JsonNode metadataNode = dataObject.get("metadata");
                java.util.Map<String, String> metadata = new java.util.HashMap<>();
                metadataNode.fields().forEachRemaining(entry -> {
                    metadata.put(entry.getKey(), entry.getValue().asText());
                });
                subscription.setMetadata(metadata);
            }
            
            // Extract items (for price information)
            if (dataObject.has("items") && dataObject.get("items").has("data")) {
                JsonNode itemsData = dataObject.get("items").get("data");
                if (itemsData.isArray() && itemsData.size() > 0) {
                    JsonNode firstItem = itemsData.get(0);
                    if (firstItem.has("price") && firstItem.get("price").has("id")) {
                        // Create a subscription item with price info
                        com.stripe.model.SubscriptionItem item = new com.stripe.model.SubscriptionItem();
                        com.stripe.model.Price price = new com.stripe.model.Price();
                        price.setId(firstItem.get("price").get("id").asText());
                        item.setPrice(price);
                        
                        // Set items on subscription
                        com.stripe.model.SubscriptionItemCollection items = new com.stripe.model.SubscriptionItemCollection();
                        items.setData(java.util.Arrays.asList(item));
                        subscription.setItems(items);
                    }
                }
            }
            
            log.info("✅ Successfully extracted subscription: {}", subscription.getId());
            return subscription;
            
        } catch (Exception e) {
            log.error("❌ Exception while extracting Subscription from event {}: {}", event.getId(), e.getMessage(), e);
            return null;
        }
    }
    
    private Invoice extractInvoice(Event event) {
        try {
            log.info("🔍 Extracting invoice from event: {}", event.getId());
            
            // Get the raw JSON data from the event
            JsonNode eventData = new ObjectMapper().readTree(event.toJson());
            JsonNode dataObject = eventData.get("data").get("object");
            
            if (dataObject == null) {
                log.error("❌ No data.object found in event {}", event.getId());
                return null;
            }
            
            // Create an invoice object from the JSON data
            Invoice invoice = new Invoice();
            
            // Extract basic fields
            if (dataObject.has("id")) {
                invoice.setId(dataObject.get("id").asText());
            }
            if (dataObject.has("subscription")) {
                invoice.setSubscription(dataObject.get("subscription").asText());
            }
            if (dataObject.has("customer")) {
                invoice.setCustomer(dataObject.get("customer").asText());
            }
            if (dataObject.has("amount_paid")) {
                invoice.setAmountPaid(dataObject.get("amount_paid").asLong());
            }
            if (dataObject.has("status")) {
                invoice.setStatus(dataObject.get("status").asText());
            }
            if (dataObject.has("total")) {
                invoice.setTotal(dataObject.get("total").asLong());
            }
            if (dataObject.has("currency")) {
                invoice.setCurrency(dataObject.get("currency").asText());
            }
            
            log.info("✅ Successfully extracted invoice: {}", invoice.getId());
            return invoice;
            
        } catch (Exception e) {
            log.error("❌ Exception while extracting Invoice from event {}: {}", event.getId(), e.getMessage(), e);
            return null;
        }
    }
    
    private void syncSubscriptionToDatabase(Subscription stripeSubscription) {
        try {
            String userIdStr = stripeSubscription.getMetadata() != null ?
                    stripeSubscription.getMetadata().get("userId") : null;

            if (userIdStr == null) {
                log.warn("⚠️ No userId in subscription metadata for subscription: {}", stripeSubscription.getId());
                return;
            }

            UUID userId = UUID.fromString(userIdStr);
            log.info("🔗 Syncing subscription to database for user: {}", userId);

            // Try to find the subscription plan by Stripe price ID
            SubscriptionPlan plan = null;
            if (stripeSubscription.getItems() != null && !stripeSubscription.getItems().getData().isEmpty()) {
                String stripePriceId = stripeSubscription.getItems().getData().get(0).getPrice().getId();
                log.info("🔍 Looking for plan with Stripe price ID: {}", stripePriceId);
                
                plan = subscriptionPlanService.findByStripePriceId(stripePriceId).orElse(null);
                if (plan == null) {
                    log.warn("⚠️ No subscription plan found for Stripe price ID: {}", stripePriceId);
                } else {
                    log.info("✅ Found plan: {} for price ID: {}", plan.getName(), stripePriceId);
                }
            }

            UserSubscription dbSubscription = new UserSubscription();
            dbSubscription.setUserId(userId);
            dbSubscription.setPlan(plan); // This could be null if plan not found
            dbSubscription.setStripeSubscriptionId(stripeSubscription.getId());
            dbSubscription.setStripeCustomerId(stripeSubscription.getCustomer());
            dbSubscription.setStatus(mapStripeStatus(stripeSubscription.getStatus()));
            dbSubscription.setAutoRenew(!stripeSubscription.getCancelAtPeriodEnd());

            // Stripe manages the dates
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
            log.info("✅ Subscription synced to database: {} (Plan: {})", 
                     dbSubscription.getId(), plan != null ? plan.getName() : "UNKNOWN");

        } catch (Exception e) {
            log.error("❌ Error syncing subscription to database", e);
        }
    }

    private void updateSubscriptionFromStripe(UserSubscription dbSubscription, Subscription stripeSubscription) {
        dbSubscription.setStatus(mapStripeStatus(stripeSubscription.getStatus()));
        dbSubscription.setAutoRenew(!stripeSubscription.getCancelAtPeriodEnd());
        
        // Update dates from Stripe - Stripe is the source of truth
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
        log.info("✅ Subscription updated in database: {}", dbSubscription.getId());
    }
    
    private void awardPointsForNewSubscription(Subscription stripeSubscription) {
        try {
            subscriptionRepository.findByStripeSubscriptionId(stripeSubscription.getId())
                    .ifPresent(dbSubscription -> {
                        if (dbSubscription.getPlan() != null) {
                            pointsService.awardPoints(
                                    dbSubscription.getUserId(),
                                    dbSubscription.getPlan().getPointsAwarded(),
                                    "New subscription: " + dbSubscription.getPlan().getName(),
                                    "SUBSCRIPTION",
                                    dbSubscription.getId()
                            );
                            log.info("🎁 Points awarded for new subscription");
                        }
                    });
        } catch (Exception e) {
            log.error("Error awarding points for subscription", e);
        }
    }
    
    private UserSubscription.SubscriptionStatus mapStripeStatus(String stripeStatus) {
        return switch (stripeStatus) {
            case "active", "trialing" -> UserSubscription.SubscriptionStatus.ACTIVE;
            case "canceled" -> UserSubscription.SubscriptionStatus.CANCELLED;
            case "incomplete", "incomplete_expired" -> UserSubscription.SubscriptionStatus.PENDING;
            case "past_due", "unpaid" -> UserSubscription.SubscriptionStatus.EXPIRED;
            default -> UserSubscription.SubscriptionStatus.PENDING;
        };
    }
    
    private void logSubscriptionDetails(Subscription subscription) {
        log.info("Subscription ID: {}", subscription.getId());
        log.info("Status: {}", subscription.getStatus());
        log.info("Customer: {}", subscription.getCustomer());
        log.info("Period: {} to {}",
                subscription.getCurrentPeriodStart() != null ?
                    Instant.ofEpochSecond(subscription.getCurrentPeriodStart()) : "null",
                subscription.getCurrentPeriodEnd() != null ?
                    Instant.ofEpochSecond(subscription.getCurrentPeriodEnd()) : "null"
        );
    }
}

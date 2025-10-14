package com.edu.subscription_service.service;

import com.edu.subscription_service.dto.UserSubscriptionDto;
import com.edu.subscription_service.dto.request.CreateSubscriptionRequest;
import com.edu.subscription_service.dto.response.PaymentIntentResponse;
import com.edu.subscription_service.entity.Payment;
import com.edu.subscription_service.entity.SubscriptionPlan;
import com.edu.subscription_service.entity.UserSubscription;
import com.edu.subscription_service.repository.SubscriptionPlanRepository;
import com.edu.subscription_service.repository.UserSubscriptionRepository;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.SetupIntent;
import com.stripe.model.Subscription;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionService {
    
    private final UserSubscriptionRepository subscriptionRepository;
    private final SubscriptionPlanRepository planRepository;
    private final StripeService stripeService;
    private final PointsService pointsService;
    private final ModelMapper modelMapper;
    private final SubscriptionEventProducer eventProducer;
    
    @Transactional
    public PaymentIntentResponse createSubscription(UUID userId, CreateSubscriptionRequest request) throws StripeException {
        log.info("🚀 Creating Stripe-first subscription for user: {} with plan: {}", userId, request.getPlanId());
        
        // Get subscription plan
        SubscriptionPlan plan = planRepository.findById(request.getPlanId())
                .orElseThrow(() -> new RuntimeException("Subscription plan not found"));
        
        // Validate Stripe price ID is configured
        if (plan.getStripePriceId() == null || plan.getStripePriceId().trim().isEmpty()) {
            throw new RuntimeException("Stripe price ID is not configured for plan: " + plan.getName());
        }

        // Check if user already has an active subscription IN STRIPE
        log.info("🔍 Checking for existing active subscriptions...");
        Optional<UserSubscription> existingSubscription = subscriptionRepository.findActiveByUserId(userId);
        if (existingSubscription.isPresent()) {
            // Double-check with Stripe to ensure it's actually active
            try {
                Subscription stripeSubscription = stripeService.getSubscription(
                        existingSubscription.get().getStripeSubscriptionId());
                
                if ("active".equals(stripeSubscription.getStatus()) || 
                    "trialing".equals(stripeSubscription.getStatus())) {
                    throw new RuntimeException("User already has an active subscription in Stripe");
                }
                
                log.info("🧹 Existing subscription found but not active in Stripe, proceeding...");
            } catch (StripeException e) {
                log.warn("Could not verify existing subscription with Stripe, proceeding: {}", e.getMessage());
            }
        }
        
        // Create or get Stripe customer
        Customer customer = null;
        try {
            customer = stripeService.createCustomer(request.getEmail(), userId);
            log.info("✅ Stripe customer ready: {}", customer.getId());
        } catch (StripeException e) {
            log.error("❌ Failed to create Stripe customer for user: {} with email: {}", userId, request.getEmail(), e);
            throw new RuntimeException("Failed to create customer: " + e.getMessage(), e);
        }

        // Create Stripe subscription - THIS IS THE SOURCE OF TRUTH
        Subscription stripeSubscription = null;
        try {
            log.info("🎯 Creating subscription in Stripe with price: {}", plan.getStripePriceId());
            stripeSubscription = stripeService.createSubscription(
                    customer.getId(),
                    plan.getStripePriceId(),
                    userId
            );
            
            log.info("✅ Stripe subscription created: {} with status: {}", 
                    stripeSubscription.getId(), stripeSubscription.getStatus());
                    
        } catch (StripeException e) {
            log.error("❌ Stripe error while creating subscription", e);
            if (e.getMessage().contains("No such price")) {
                throw new RuntimeException("Invalid Stripe price ID '" + plan.getStripePriceId() + "' for plan: " + plan.getName() +
                    ". Please create this price in your Stripe dashboard or update the plan configuration.", e);
            }
            throw new RuntimeException("Failed to create Stripe subscription: " + e.getMessage(), e);
        }

        // Create database record as a MIRROR/LOG of Stripe subscription
        UserSubscription dbSubscription = createDatabaseSubscriptionRecord(stripeSubscription, plan, userId);
        log.info("📝 Database subscription record created as log: {}", dbSubscription.getId());

        //Created kafka producer event
//        SubscriptionEvent event = new SubscriptionEvent(
//                userId.toString(),
//                dbSubscription.getId().toString(),
//                "SUBSCRIPTION_CREATED",
//                "Subscription created for plan: " + plan.getName()
//        );
//        eventProducer.sendEvent(event);

        // Extract payment information from Stripe subscription
        PaymentIntentResponse paymentResponse = extractPaymentIntentFromSubscription(stripeSubscription, dbSubscription.getId());
        
        if (paymentResponse != null) {
            log.info("💳 Payment intent ready for frontend: {}", paymentResponse.getPaymentIntentId());
            
            // Create payment record for tracking
            stripeService.createPaymentRecord(
                    userId,
                    dbSubscription,
                    plan.getPrice(),
                    plan.getCurrency(),
                    paymentResponse.getPaymentIntentId()
            );
        } else {
            log.warn("⚠️ No payment intent found in subscription - subscription might be active immediately");
        }
        
        log.info("🎉 Subscription creation completed - Stripe is the source of truth!");
        return paymentResponse;
    }
    
    private UserSubscription createDatabaseSubscriptionRecord(Subscription stripeSubscription, SubscriptionPlan plan, UUID userId) {
        log.info("📝 Creating database record to mirror Stripe subscription: {}", stripeSubscription.getId());
        
        UserSubscription userSubscription = new UserSubscription();
        userSubscription.setUserId(userId);
        userSubscription.setPlan(plan);
        userSubscription.setStripeSubscriptionId(stripeSubscription.getId());
        userSubscription.setStripeCustomerId(stripeSubscription.getCustomer());
        userSubscription.setAutoRenew(!stripeSubscription.getCancelAtPeriodEnd());
        
        // Set status based on STRIPE status, not our own logic
        userSubscription.setStatus(mapStripeStatusToLocal(stripeSubscription.getStatus()));
        
        // Set dates from Stripe
        if (stripeSubscription.getCurrentPeriodStart() != null) {
            userSubscription.setStartDate(LocalDateTime.ofInstant(
                    Instant.ofEpochSecond(stripeSubscription.getCurrentPeriodStart()), 
                    ZoneId.systemDefault()));
        } else {
            userSubscription.setStartDate(LocalDateTime.now());
        }
        
        if (stripeSubscription.getCurrentPeriodEnd() != null) {
            userSubscription.setEndDate(LocalDateTime.ofInstant(
                    Instant.ofEpochSecond(stripeSubscription.getCurrentPeriodEnd()), 
                    ZoneId.systemDefault()));
        } else {
            userSubscription.setEndDate(calculateEndDate(plan.getBillingCycle()));
        }
        
        UserSubscription savedSubscription = subscriptionRepository.save(userSubscription);
        log.info("✅ Database subscription record saved with Stripe status: {}", savedSubscription.getStatus());
        
        return savedSubscription;
    }
    
    private void createPaymentRecordFromSubscription(Subscription stripeSubscription, UserSubscription userSubscription, SubscriptionPlan plan, UUID userId) {
        log.info("💳 Creating payment record for subscription: {}", stripeSubscription.getId());
        
        try {
            // Check if subscription has a latest invoice
            if (stripeSubscription.getLatestInvoice() != null) {
                Object latestInvoiceObj = stripeSubscription.getLatestInvoice();
                
                if (latestInvoiceObj instanceof com.stripe.model.Invoice) {
                    com.stripe.model.Invoice invoice = (com.stripe.model.Invoice) latestInvoiceObj;
                    log.info("📄 Found invoice: {} with status: {}", invoice.getId(), invoice.getStatus());
                    
                    // Create payment record based on invoice
                    Payment.PaymentStatus paymentStatus;
                    String paymentIntentId = null;
                    
                    if (invoice.getPaymentIntent() != null) {
                        Object paymentIntentObj = invoice.getPaymentIntent();
                        if (paymentIntentObj instanceof com.stripe.model.PaymentIntent) {
                            com.stripe.model.PaymentIntent paymentIntent = (com.stripe.model.PaymentIntent) paymentIntentObj;
                            paymentIntentId = paymentIntent.getId();
                            log.info("💰 Payment intent found: {} with status: {}", paymentIntent.getId(), paymentIntent.getStatus());
                            
                            // Map Stripe payment intent status to our payment status
                            paymentStatus = switch (paymentIntent.getStatus()) {
                                case "succeeded" -> Payment.PaymentStatus.COMPLETED;
                                case "processing" -> Payment.PaymentStatus.PENDING;
                                case "requires_payment_method", "requires_confirmation", "requires_action" -> Payment.PaymentStatus.PENDING;
                                case "canceled" -> Payment.PaymentStatus.FAILED;
                                default -> Payment.PaymentStatus.PENDING;
                            };
                        } else {
                            // No payment intent, determine status from invoice
                            paymentStatus = switch (invoice.getStatus()) {
                                case "paid" -> Payment.PaymentStatus.COMPLETED;
                                case "open", "draft" -> Payment.PaymentStatus.PENDING;
                                case "void", "uncollectible" -> Payment.PaymentStatus.FAILED;
                                default -> Payment.PaymentStatus.PENDING;
                            };
                        }
                    } else {
                        // No payment intent, determine status from invoice
                        paymentStatus = switch (invoice.getStatus()) {
                            case "paid" -> Payment.PaymentStatus.COMPLETED;
                            case "open", "draft" -> Payment.PaymentStatus.PENDING;
                            case "void", "uncollectible" -> Payment.PaymentStatus.FAILED;
                            default -> Payment.PaymentStatus.PENDING;
                        };
                    }
                    
                    // Create payment record
                    Payment payment = stripeService.createPaymentRecord(
                            userId, 
                            userSubscription, 
                            plan.getPrice(), 
                            "USD", 
                            paymentIntentId != null ? paymentIntentId : invoice.getId()
                    );
                    payment.setStatus(paymentStatus);
                    payment.setStripeInvoiceId(invoice.getId());
                    // Note: createdAt is automatically set by @CreationTimestamp
                    
                    // Payment is saved automatically by stripeService.createPaymentRecord()
                    log.info("✅ Payment record created: {} with status: {}", payment.getId(), payment.getStatus());
                    
                } else if (latestInvoiceObj instanceof String) {
                    // Invoice ID only, need to fetch full invoice
                    String invoiceId = (String) latestInvoiceObj;
                    log.info("📄 Found invoice ID: {}, creating basic payment record", invoiceId);
                    
                    // Create basic payment record
                    Payment payment = stripeService.createPaymentRecord(
                            userId, 
                            userSubscription, 
                            plan.getPrice(), 
                            "USD", 
                            invoiceId
                    );
                    payment.setStatus(Payment.PaymentStatus.PENDING);
                    payment.setStripeInvoiceId(invoiceId);
                    // Note: createdAt is automatically set by @CreationTimestamp
                    
                    log.info("✅ Basic payment record created for invoice: {}", invoiceId);
                }
            } else {
                // No invoice yet, create pending payment record
                log.info("📄 No invoice found, creating pending payment record");
                
                Payment payment = stripeService.createPaymentRecord(
                        userId, 
                        userSubscription, 
                        plan.getPrice(), 
                        "USD", 
                        stripeSubscription.getId() // Use subscription ID as reference
                );
                payment.setStatus(Payment.PaymentStatus.PENDING);
                // Note: createdAt is automatically set by @CreationTimestamp
                
                log.info("✅ Pending payment record created for subscription: {}", stripeSubscription.getId());
            }
            
        } catch (Exception e) {
            log.error("❌ Error creating payment record for subscription: {}", stripeSubscription.getId(), e);
            // Don't throw exception here - subscription should still be created even if payment record fails
        }
    }
    
    private PaymentIntentResponse extractPaymentIntentFromSubscription(Subscription stripeSubscription, UUID subscriptionId) {
        try {
            // Check if subscription has a latest invoice with payment intent
            if (stripeSubscription.getLatestInvoice() != null) {
                Object latestInvoiceObj = stripeSubscription.getLatestInvoice();
                
                if (latestInvoiceObj instanceof com.stripe.model.Invoice) {
                    com.stripe.model.Invoice invoice = (com.stripe.model.Invoice) latestInvoiceObj;
                    
                    if (invoice.getPaymentIntent() != null) {
                        Object paymentIntentObj = invoice.getPaymentIntent();
                        
                        if (paymentIntentObj instanceof com.stripe.model.PaymentIntent) {
                            com.stripe.model.PaymentIntent paymentIntent = (com.stripe.model.PaymentIntent) paymentIntentObj;
                            
                            log.info("💳 Found payment intent in subscription: {} (status: {})", 
                                    paymentIntent.getId(), paymentIntent.getStatus());
                            
                            PaymentIntentResponse response = new PaymentIntentResponse(
                                    paymentIntent.getClientSecret(),
                                    paymentIntent.getId(),
                                    paymentIntent.getStatus()
                            );
                            response.setSubscriptionId(subscriptionId);
                            return response;
                        }
                    }
                }
            }
            
            log.info("ℹ️ No payment intent required - subscription may be immediately active");
            return null;
            
        } catch (Exception e) {
            log.error("Error extracting payment intent from subscription", e);
            return null;
        }
    }
    
    private UserSubscription.SubscriptionStatus mapStripeStatusToLocal(String stripeStatus) {
        return switch (stripeStatus) {
            case "active", "trialing" -> UserSubscription.SubscriptionStatus.ACTIVE;
            case "canceled" -> UserSubscription.SubscriptionStatus.CANCELLED;
            case "incomplete", "incomplete_expired" -> UserSubscription.SubscriptionStatus.PENDING;
            case "past_due", "unpaid" -> UserSubscription.SubscriptionStatus.EXPIRED;
            default -> {
                log.warn("Unknown Stripe status: {}, defaulting to PENDING", stripeStatus);
                yield UserSubscription.SubscriptionStatus.PENDING;
            }
        };
    }

    @Transactional
    public void activateSubscription(UUID subscriptionId) {
        log.info("Manual activation of subscription: {}", subscriptionId);

        UserSubscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new RuntimeException("Subscription not found with ID: " + subscriptionId));

        log.info("Found subscription: {} with status: {}", subscription.getId(), subscription.getStatus());

        if (subscription.getStatus() == UserSubscription.SubscriptionStatus.ACTIVE) {
            log.info("Subscription {} is already active. Skipping activation.", subscriptionId);
            return;
        }

        subscription.setStatus(UserSubscription.SubscriptionStatus.ACTIVE);
        UserSubscription savedSubscription = subscriptionRepository.save(subscription);
        log.info("Updated subscription status to: {}", savedSubscription.getStatus());

        // Award points for subscription
        try {
            pointsService.awardPoints(
                    subscription.getUserId(),
                    subscription.getPlan().getPointsAwarded(),
                    "Subscribed to " + subscription.getPlan().getName() + " plan",
                    "SUBSCRIPTION",
                    subscription.getId()
            );
            log.info("Points awarded successfully for subscription: {}", subscriptionId);
        } catch (Exception e) {
            log.error("Failed to award points for subscription: {}", subscriptionId, e);
            // Don't fail the entire transaction for points awarding issues
        }

        log.info("Subscription activated successfully: {}", subscriptionId);
    }
    
    public List<UserSubscriptionDto> getUserSubscriptions(UUID userId) {
        log.info("📋 Fetching subscriptions for user: {}", userId);
        
        List<UserSubscription> subscriptions = subscriptionRepository.findByUserId(userId);
        
        // Optionally sync with Stripe to ensure accuracy
        for (UserSubscription subscription : subscriptions) {
            if (subscription.getStripeSubscriptionId() != null) {
                try {
                    Subscription stripeSubscription = stripeService.getSubscription(subscription.getStripeSubscriptionId());
                    
                    // Quick sync of status
                    UserSubscription.SubscriptionStatus stripeStatus = mapStripeStatusToLocal(stripeSubscription.getStatus());
                    if (stripeStatus != subscription.getStatus()) {
                        log.info("🔄 Syncing subscription status: {} → {}", subscription.getStatus(), stripeStatus);
                        subscription.setStatus(stripeStatus);
                        subscriptionRepository.save(subscription);
                    }
                } catch (Exception e) {
                    log.warn("Could not sync subscription {} with Stripe: {}", subscription.getId(), e.getMessage());
                }
            }
        }
        
        return subscriptions.stream()
                .map(sub -> modelMapper.map(sub, UserSubscriptionDto.class))
                .collect(Collectors.toList());
    }
    
    public Optional<UserSubscriptionDto> getActiveSubscription(UUID userId) {
        log.info("🎯 Fetching active subscription for user: {}", userId);
        
        Optional<UserSubscription> subscription = subscriptionRepository.findActiveByUserId(userId);
        
        // Verify with Stripe that it's actually active
        if (subscription.isPresent() && subscription.get().getStripeSubscriptionId() != null) {
            try {
                Subscription stripeSubscription = stripeService.getSubscription(subscription.get().getStripeSubscriptionId());
                
                boolean isReallyActive = "active".equals(stripeSubscription.getStatus()) || 
                                        "trialing".equals(stripeSubscription.getStatus());
                
                if (!isReallyActive) {
                    log.info("⚠️ Subscription in DB shows active but Stripe shows: {}", stripeSubscription.getStatus());
                    
                    // Update our database to match Stripe
                    UserSubscription dbSub = subscription.get();
                    dbSub.setStatus(mapStripeStatusToLocal(stripeSubscription.getStatus()));
                    subscriptionRepository.save(dbSub);
                    
                    return Optional.empty(); // Not actually active
                }
                
                log.info("✅ Confirmed active subscription in Stripe: {}", stripeSubscription.getId());
                
            } catch (Exception e) {
                log.warn("Could not verify subscription with Stripe: {}", e.getMessage());
            }
        }
        
        return subscription.map(sub -> modelMapper.map(sub, UserSubscriptionDto.class));
    }
    
    @Transactional
    public void cancelSubscription(UUID subscriptionId) throws StripeException {
        log.info("🚫 Cancelling subscription: {}", subscriptionId);
        
        UserSubscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new RuntimeException("Subscription not found"));
        
        // Cancel in Stripe FIRST - this is the source of truth
        if (subscription.getStripeSubscriptionId() != null) {
            try {
                log.info("🎯 Cancelling in Stripe: {}", subscription.getStripeSubscriptionId());
                stripeService.cancelSubscription(subscription.getStripeSubscriptionId());
                log.info("✅ Successfully cancelled in Stripe");
                
                // The webhook will handle updating our database
                log.info("📡 Webhook will update database status automatically");
                
            } catch (StripeException e) {
                log.error("❌ Failed to cancel subscription in Stripe", e);
                throw e;
            }
        } else {
            log.warn("⚠️ No Stripe subscription ID found, updating local record only");
            subscription.setStatus(UserSubscription.SubscriptionStatus.CANCELLED);
            subscription.setAutoRenew(false);
            subscriptionRepository.save(subscription);
        }
        
        log.info("🎉 Subscription cancellation completed");
    }
    
    @Transactional
    public void expireSubscriptions() {
        log.info("🕐 Processing expired subscriptions (checking against Stripe)");
        
        List<UserSubscription> potentiallyExpired = subscriptionRepository.findExpiredSubscriptions(LocalDateTime.now());
        
        for (UserSubscription subscription : potentiallyExpired) {
            if (subscription.getStripeSubscriptionId() != null) {
                try {
                    // Check actual status in Stripe
                    Subscription stripeSubscription = stripeService.getSubscription(subscription.getStripeSubscriptionId());
                    
                    UserSubscription.SubscriptionStatus actualStatus = mapStripeStatusToLocal(stripeSubscription.getStatus());
                    
                    if (actualStatus != subscription.getStatus()) {
                        log.info("🔄 Syncing expired subscription {} status: {} → {}", 
                                subscription.getId(), subscription.getStatus(), actualStatus);
                        
                        subscription.setStatus(actualStatus);
                        
                        // Update dates from Stripe
                        if (stripeSubscription.getCurrentPeriodEnd() != null) {
                            subscription.setEndDate(LocalDateTime.ofInstant(
                                    Instant.ofEpochSecond(stripeSubscription.getCurrentPeriodEnd()), 
                                    ZoneId.systemDefault()));
                        }
                        
                        subscriptionRepository.save(subscription);
                    }
                    
                } catch (Exception e) {
                    log.error("Error checking subscription {} with Stripe: {}", subscription.getId(), e.getMessage());
                    
                    // If we can't reach Stripe, mark as expired locally
                    subscription.setStatus(UserSubscription.SubscriptionStatus.EXPIRED);
                    subscriptionRepository.save(subscription);
                }
            } else {
                // No Stripe ID, just mark as expired
                subscription.setStatus(UserSubscription.SubscriptionStatus.EXPIRED);
                subscriptionRepository.save(subscription);
                log.info("Expired local subscription: {}", subscription.getId());
            }
        }
    }
    
    @Transactional
    public Map<String, Object> createSubscriptionSetup(CreateSubscriptionRequest request, UUID userId) {
        log.info("🚀 Creating subscription setup for user: {} with plan: {}", userId, request.getPlanId());
        
        try {
            // Get the subscription plan
            SubscriptionPlan plan = planRepository.findById(request.getPlanId())
                    .orElseThrow(() -> new RuntimeException("Subscription plan not found"));
            log.info("📋 Plan details: {} - ${} | Stripe Price ID: {} | Billing: {}", 
                    plan.getName(), plan.getPrice(), plan.getStripePriceId(), plan.getBillingCycle());
            
            // Create or get Stripe customer
            Customer customer = stripeService.createCustomer(request.getEmail(), userId);
            log.info("✅ Stripe customer ready: {}", customer.getId());
            
            // Create setup intent for payment method collection
            SetupIntent setupIntent = stripeService.createSetupIntent(customer.getId(), userId);
            log.info("🔧 Setup intent created: {}", setupIntent.getId());
            
            // Return setup details to frontend
            Map<String, Object> response = new HashMap<>();
            response.put("setup_intent_client_secret", setupIntent.getClientSecret());
            response.put("customer_id", customer.getId());
            response.put("plan_id", request.getPlanId());
            response.put("plan_name", plan.getName());
            response.put("plan_price", plan.getPrice());
            response.put("status", "setup_required");
            
            return response;
            
        } catch (StripeException e) {
            log.error("❌ Stripe error during subscription setup", e);
            throw new RuntimeException("Failed to create subscription setup: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("❌ Error during subscription setup", e);
            throw new RuntimeException("Failed to create subscription setup: " + e.getMessage(), e);
        }
    }
    
    @Transactional
    public UserSubscriptionDto createSubscriptionAfterSetup(String customerId, UUID planId, UUID userId) {
        log.info("🚀 Creating subscription after setup for user: {} with plan: {}", userId, planId);
        
        try {
            // Get the subscription plan
            SubscriptionPlan plan = planRepository.findById(planId)
                    .orElseThrow(() -> new RuntimeException("Subscription plan not found"));
            log.info("📋 Plan details: {} - ${} | Stripe Price ID: {} | Billing: {}", 
                    plan.getName(), plan.getPrice(), plan.getStripePriceId(), plan.getBillingCycle());
            
            // CRITICAL: Ensure customer has payment method before creating subscription
            stripeService.ensureCustomerHasPaymentMethod(customerId);
            log.info("✅ Payment method verification completed for customer: {}", customerId);
            
            // Create subscription in Stripe (should work now with payment method attached)
            Subscription stripeSubscription = stripeService.createSubscription(
                    customerId, plan.getStripePriceId(), userId);
            log.info("✅ Stripe subscription created: {} with status: {}", 
                    stripeSubscription.getId(), stripeSubscription.getStatus());
            
            // Create local subscription record
            UserSubscription userSubscription = createDatabaseSubscriptionRecord(stripeSubscription, plan, userId);
            log.info("💾 Database subscription record created: {}", userSubscription.getId());
            
            // Create payment record if subscription is active or has payment intent
            createPaymentRecordFromSubscription(stripeSubscription, userSubscription, plan, userId);
            
            // Award points if subscription is active
            if (userSubscription.getStatus() == UserSubscription.SubscriptionStatus.ACTIVE) {
                try {
                    pointsService.awardPoints(
                            userId,
                            plan.getPointsAwarded(),
                            "Subscribed to " + plan.getName() + " plan",
                            "SUBSCRIPTION",
                            userSubscription.getId()
                    );
                    log.info("🎯 Points awarded: {} points for subscription {}", plan.getPointsAwarded(), userSubscription.getId());
                } catch (Exception e) {
                    log.error("❌ Failed to award points for subscription: {}", userSubscription.getId(), e);
                }
            }
            
            return modelMapper.map(userSubscription, UserSubscriptionDto.class);
            
        } catch (StripeException e) {
            log.error("❌ Stripe error during subscription creation", e);
            throw new RuntimeException("Failed to create subscription: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("❌ Error during subscription creation", e);
            throw new RuntimeException("Failed to create subscription: " + e.getMessage(), e);
        }
    }
    
    public Map<String, Object> debugPlan(UUID planId) {
        try {
            SubscriptionPlan plan = planRepository.findById(planId)
                    .orElseThrow(() -> new RuntimeException("Plan not found"));
            
            Map<String, Object> debugInfo = new HashMap<>();
            debugInfo.put("database_plan_id", plan.getId());
            debugInfo.put("database_plan_name", plan.getName());
            debugInfo.put("database_plan_price", plan.getPrice());
            debugInfo.put("database_stripe_price_id", plan.getStripePriceId());
            debugInfo.put("database_billing_cycle", plan.getBillingCycle());
            debugInfo.put("database_points_awarded", plan.getPointsAwarded());
            
            // Try to fetch from Stripe to compare
            try {
                com.stripe.model.Price stripePrice = com.stripe.model.Price.retrieve(plan.getStripePriceId());
                debugInfo.put("stripe_price_id", stripePrice.getId());
                debugInfo.put("stripe_unit_amount", stripePrice.getUnitAmount());
                debugInfo.put("stripe_currency", stripePrice.getCurrency());
                debugInfo.put("stripe_product_id", stripePrice.getProduct());
                
                // Get product details
                com.stripe.model.Product stripeProduct = com.stripe.model.Product.retrieve(stripePrice.getProduct());
                debugInfo.put("stripe_product_name", stripeProduct.getName());
                debugInfo.put("stripe_product_description", stripeProduct.getDescription());
            } catch (Exception e) {
                debugInfo.put("stripe_error", e.getMessage());
            }
            
            return debugInfo;
        } catch (Exception e) {
            Map<String, Object> errorInfo = new HashMap<>();
            errorInfo.put("error", e.getMessage());
            return errorInfo;
        }
    }
    
    private LocalDateTime calculateEndDate(SubscriptionPlan.BillingCycle billingCycle) {
        LocalDateTime now = LocalDateTime.now();
        return switch (billingCycle) {
            case MONTHLY -> now.plusMonths(1);
            case YEARLY -> now.plusYears(1);
        };
    }
}

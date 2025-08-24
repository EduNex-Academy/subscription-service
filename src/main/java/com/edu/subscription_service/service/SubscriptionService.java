package com.edu.subscription_service.service;

import com.edu.subscription_service.dto.UserSubscriptionDto;
import com.edu.subscription_service.dto.request.CreateSubscriptionRequest;
import com.edu.subscription_service.dto.response.PaymentIntentResponse;
import com.edu.subscription_service.entity.SubscriptionPlan;
import com.edu.subscription_service.entity.UserSubscription;
import com.edu.subscription_service.repository.SubscriptionPlanRepository;
import com.edu.subscription_service.repository.UserSubscriptionRepository;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.Subscription;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
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
    
    @Transactional
    public PaymentIntentResponse createSubscription(UUID userId, CreateSubscriptionRequest request) throws StripeException {
        log.info("Creating subscription for user: {} with plan: {}", userId, request.getPlanId());
        
        // Get subscription plan
        SubscriptionPlan plan = planRepository.findById(request.getPlanId())
                .orElseThrow(() -> new RuntimeException("Subscription plan not found"));
        
        // Validate Stripe price ID is configured
        if (plan.getStripePriceId() == null || plan.getStripePriceId().trim().isEmpty()) {
            throw new RuntimeException("Stripe price ID is not configured for plan: " + plan.getName());
        }

        // Check if user already has an active subscription
        Optional<UserSubscription> existingSubscription = subscriptionRepository.findActiveByUserId(userId);
        if (existingSubscription.isPresent()) {
            throw new RuntimeException("User already has an active subscription");
        }
        
        // Create Stripe customer
        Customer customer = null;
        try {
            customer = stripeService.createCustomer(request.getEmail(), userId);
        } catch (StripeException e) {
            log.error("Failed to create Stripe customer for user: {} with email: {}", userId, request.getEmail(), e);
            throw new RuntimeException("Failed to create customer: " + e.getMessage(), e);
        }

        // Create Stripe subscription
        Subscription stripeSubscription = null;
        try {
            stripeSubscription = stripeService.createSubscription(
                    customer.getId(),
                    plan.getStripePriceId(),
                    userId
            );
        } catch (StripeException e) {
            log.error("Stripe error while creating subscription", e);
            if (e.getMessage().contains("No such price")) {
                throw new RuntimeException("Invalid Stripe price ID '" + plan.getStripePriceId() + "' for plan: " + plan.getName() +
                    ". Please create this price in your Stripe dashboard or update the plan configuration.", e);
            }
            throw new RuntimeException("Failed to create Stripe subscription: " + e.getMessage(), e);
        }

        // Create user subscription record
        UserSubscription userSubscription = new UserSubscription();
        userSubscription.setUserId(userId);
        userSubscription.setPlan(plan);
        userSubscription.setStatus(UserSubscription.SubscriptionStatus.PENDING);
        userSubscription.setStripeSubscriptionId(stripeSubscription.getId());
        userSubscription.setStripeCustomerId(customer.getId());
        userSubscription.setStartDate(LocalDateTime.now());
        userSubscription.setEndDate(calculateEndDate(plan.getBillingCycle()));
        userSubscription.setAutoRenew(true);
        
        UserSubscription savedSubscription = subscriptionRepository.save(userSubscription);
        log.info("Created user subscription with id: {}", savedSubscription.getId());
        
        // Create payment intent for the subscription
        PaymentIntentResponse paymentIntent = stripeService.createPaymentIntent(
                plan.getPrice(),
                plan.getCurrency(),
                customer.getId(),
                userId
        );
        
        // Add subscription ID to the response
        paymentIntent.setSubscriptionId(savedSubscription.getId());

        // Create payment record
        stripeService.createPaymentRecord(
                userId,
                savedSubscription,
                plan.getPrice(),
                plan.getCurrency(),
                paymentIntent.getPaymentIntentId()
        );
        
        return paymentIntent;
    }

    @Transactional
    public void activateSubscription(UUID subscriptionId) {
        log.info("Activating subscription: {}", subscriptionId);

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
    
    @Transactional
    public void cancelSubscription(UUID subscriptionId) throws StripeException {
        log.info("Cancelling subscription: {}", subscriptionId);
        
        UserSubscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new RuntimeException("Subscription not found"));
        
        // Cancel in Stripe
        if (subscription.getStripeSubscriptionId() != null) {
            stripeService.cancelSubscription(subscription.getStripeSubscriptionId());
        }
        
        subscription.setStatus(UserSubscription.SubscriptionStatus.CANCELLED);
        subscription.setAutoRenew(false);
        subscriptionRepository.save(subscription);
        
        log.info("Subscription cancelled: {}", subscriptionId);
    }
    
    public List<UserSubscriptionDto> getUserSubscriptions(UUID userId) {
        log.info("Fetching subscriptions for user: {}", userId);
        List<UserSubscription> subscriptions = subscriptionRepository.findByUserId(userId);
        return subscriptions.stream()
                .map(sub -> modelMapper.map(sub, UserSubscriptionDto.class))
                .collect(Collectors.toList());
    }
    
    public Optional<UserSubscriptionDto> getActiveSubscription(UUID userId) {
        log.info("Fetching active subscription for user: {}", userId);
        Optional<UserSubscription> subscription = subscriptionRepository.findActiveByUserId(userId);
        return subscription.map(sub -> modelMapper.map(sub, UserSubscriptionDto.class));
    }
    
    @Transactional
    public void expireSubscriptions() {
        log.info("Processing expired subscriptions");
        List<UserSubscription> expiredSubscriptions = subscriptionRepository.findExpiredSubscriptions(LocalDateTime.now());
        
        for (UserSubscription subscription : expiredSubscriptions) {
            subscription.setStatus(UserSubscription.SubscriptionStatus.EXPIRED);
            subscriptionRepository.save(subscription);
            log.info("Expired subscription: {}", subscription.getId());
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

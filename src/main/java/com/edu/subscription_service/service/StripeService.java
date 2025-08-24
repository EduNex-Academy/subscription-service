package com.edu.subscription_service.service;

import com.edu.subscription_service.dto.response.PaymentIntentResponse;
import com.edu.subscription_service.entity.Payment;
import com.edu.subscription_service.entity.UserSubscription;
import com.edu.subscription_service.repository.PaymentRepository;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Subscription;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.SubscriptionCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class StripeService {
    
    private final PaymentRepository paymentRepository;
    
    public Customer createCustomer(String email, UUID userId) throws StripeException {
        log.info("Creating Stripe customer for email: {} and userId: {}", email, userId);
        
        // Check if customer already exists
        Customer existingCustomer = findCustomerByEmail(email);
        if (existingCustomer != null) {
            log.info("Customer already exists: {}", existingCustomer.getId());
            return existingCustomer;
        }
        
        Map<String, String> metadata = new HashMap<>();
        metadata.put("userId", userId.toString());
        
        CustomerCreateParams params = CustomerCreateParams.builder()
                .setEmail(email)
                .putAllMetadata(metadata)
                .build();
        
        Customer customer = Customer.create(params);
        log.info("Created Stripe customer: {}", customer.getId());
        return customer;
    }
    
    public Customer findCustomerByEmail(String email) throws StripeException {
        try {
            var params = com.stripe.param.CustomerListParams.builder()
                    .setEmail(email)
                    .setLimit(1L)
                    .build();
            
            var customers = Customer.list(params);
            return customers.getData().isEmpty() ? null : customers.getData().get(0);
        } catch (Exception e) {
            log.warn("Error searching for customer by email: {}", email, e);
            return null;
        }
    }
    
    public PaymentIntentResponse createPaymentIntent(BigDecimal amount, String currency, String customerId, UUID userId) throws StripeException {
        log.info("Creating payment intent for amount: {} {} for customer: {}", amount, currency, customerId);
        
        Map<String, String> metadata = new HashMap<>();
        metadata.put("userId", userId.toString());
        metadata.put("customerId", customerId);
        
        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                .setAmount(amount.multiply(BigDecimal.valueOf(100)).longValue()) // Convert to cents
                .setCurrency(currency.toLowerCase())
                .setCustomer(customerId)
                .putAllMetadata(metadata)
                .setAutomaticPaymentMethods(
                        PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                                .setEnabled(true)
                                .build()
                )
                .build();
        
        PaymentIntent paymentIntent = PaymentIntent.create(params);
        log.info("Created payment intent: {}", paymentIntent.getId());
        
        return new PaymentIntentResponse(
                paymentIntent.getClientSecret(),
                paymentIntent.getId(),
                paymentIntent.getStatus()
        );
    }
    
    public Subscription createSubscription(String customerId, String priceId, UUID userId) throws StripeException {
        log.info("Creating Stripe subscription for customer: {} with price: {}", customerId, priceId);
        
        Map<String, String> metadata = new HashMap<>();
        metadata.put("userId", userId.toString());
        
        SubscriptionCreateParams params = SubscriptionCreateParams.builder()
                .setCustomer(customerId)
                .addItem(
                        SubscriptionCreateParams.Item.builder()
                                .setPrice(priceId)
                                .build()
                )
                .putAllMetadata(metadata)
                .setPaymentBehavior(SubscriptionCreateParams.PaymentBehavior.DEFAULT_INCOMPLETE)
                .setPaymentSettings(
                        SubscriptionCreateParams.PaymentSettings.builder()
                                .setSaveDefaultPaymentMethod(SubscriptionCreateParams.PaymentSettings.SaveDefaultPaymentMethod.ON_SUBSCRIPTION)
                                .build()
                )
                .addAllExpand(java.util.Arrays.asList("latest_invoice.payment_intent"))
                .build();
        
        Subscription subscription = Subscription.create(params);
        log.info("Created Stripe subscription: {}", subscription.getId());
        return subscription;
    }
    
    @Transactional
    public Payment createPaymentRecord(UUID userId, UserSubscription subscription, BigDecimal amount, String currency, String paymentIntentId) {
        log.info("Creating payment record for user: {} with payment intent: {}", userId, paymentIntentId);
        
        Payment payment = new Payment();
        payment.setUserId(userId);
        payment.setSubscription(subscription);
        payment.setAmount(amount);
        payment.setCurrency(currency);
        payment.setStatus(Payment.PaymentStatus.PENDING);
        payment.setPaymentMethod("STRIPE");
        payment.setStripePaymentIntentId(paymentIntentId);
        
        Payment savedPayment = paymentRepository.save(payment);
        log.info("Created payment record with id: {}", savedPayment.getId());
        return savedPayment;
    }
    
    @Transactional
    public void updatePaymentStatus(String paymentIntentId, Payment.PaymentStatus status, String failureReason) {
        log.info("Updating payment status for payment intent: {} to: {}", paymentIntentId, status);
        
        paymentRepository.findByStripePaymentIntentId(paymentIntentId)
                .ifPresent(payment -> {
                    payment.setStatus(status);
                    payment.setFailureReason(failureReason);
                    if (status == Payment.PaymentStatus.COMPLETED) {
                        payment.setProcessedAt(LocalDateTime.now());
                    }
                    paymentRepository.save(payment);
                    log.info("Updated payment status for id: {}", payment.getId());
                });
    }
    
    public PaymentIntent getPaymentIntent(String paymentIntentId) throws StripeException {
        log.info("Retrieving PaymentIntent from Stripe API: {}", paymentIntentId);
        PaymentIntent paymentIntent = PaymentIntent.retrieve(paymentIntentId);
        log.info("Successfully retrieved PaymentIntent: {}", paymentIntentId);
        return paymentIntent;
    }

    public void cancelSubscription(String subscriptionId) throws StripeException {
        log.info("Cancelling Stripe subscription: {}", subscriptionId);
        Subscription subscription = Subscription.retrieve(subscriptionId);
        subscription.cancel();
        log.info("Cancelled Stripe subscription: {}", subscriptionId);
    }
}

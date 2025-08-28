package com.edu.subscription_service.service;

import com.edu.subscription_service.dto.response.PaymentIntentResponse;
import com.edu.subscription_service.entity.Payment;
import com.edu.subscription_service.entity.UserSubscription;
import com.edu.subscription_service.repository.PaymentRepository;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.CustomerCollection;
import com.stripe.model.PaymentIntent;
import com.stripe.model.SetupIntent;
import com.stripe.model.Subscription;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.CustomerUpdateParams;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.SetupIntentCreateParams;
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
        
        // Check if customer already exists by userId first, then by email
        Customer existingCustomer = findCustomerByUserId(userId);
        if (existingCustomer != null) {
            log.info("Customer already exists by userId: {}", existingCustomer.getId());
            return existingCustomer;
        }
        
        existingCustomer = findCustomerByEmail(email);
        if (existingCustomer != null) {
            log.info("Customer already exists by email: {}", existingCustomer.getId());
            // Update metadata to include userId if missing
            if (existingCustomer.getMetadata() == null || !existingCustomer.getMetadata().containsKey("userId")) {
                Map<String, String> metadata = new HashMap<>(existingCustomer.getMetadata() != null ? existingCustomer.getMetadata() : new HashMap<>());
                metadata.put("userId", userId.toString());
                
                com.stripe.param.CustomerUpdateParams updateParams = com.stripe.param.CustomerUpdateParams.builder()
                        .putAllMetadata(metadata)
                        .build();
                
                existingCustomer = existingCustomer.update(updateParams);
                log.info("Updated customer metadata with userId: {}", existingCustomer.getId());
            }
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

    public Customer findCustomerByUserId(UUID userId) throws StripeException {
        try {
            // Search by metadata using proper parameter format
            Map<String, Object> params = new HashMap<>();
            params.put("limit", 1);
            
            CustomerCollection customers = Customer.list(params);
            
            // Filter by metadata since direct metadata search isn't working
            for (Customer customer : customers.getData()) {
                if (customer.getMetadata() != null && 
                    userId.toString().equals(customer.getMetadata().get("userId"))) {
                    return customer;
                }
            }
            return null;
        } catch (Exception e) {
            log.warn("Error searching for customer by userId: {}", userId, e);
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
        metadata.put("created_by", "subscription_service");
        
        SubscriptionCreateParams params = SubscriptionCreateParams.builder()
                .setCustomer(customerId)
                .addItem(
                        SubscriptionCreateParams.Item.builder()
                                .setPrice(priceId)
                                .build()
                )
                .putAllMetadata(metadata)
                // Use ERROR_IF_INCOMPLETE to automatically use the default payment method if available
                .setPaymentBehavior(SubscriptionCreateParams.PaymentBehavior.ERROR_IF_INCOMPLETE)
                .setCollectionMethod(SubscriptionCreateParams.CollectionMethod.CHARGE_AUTOMATICALLY)
                .setPaymentSettings(
                        SubscriptionCreateParams.PaymentSettings.builder()
                                .setSaveDefaultPaymentMethod(SubscriptionCreateParams.PaymentSettings.SaveDefaultPaymentMethod.ON_SUBSCRIPTION)
                                .build()
                )
                // Expand to get the full invoice and payment intent
                .addAllExpand(java.util.Arrays.asList("latest_invoice.payment_intent", "customer"))
                .build();
        
        Subscription subscription = Subscription.create(params);
        log.info("✅ Created Stripe subscription: {} with status: {} | Payment will be attempted automatically", 
                subscription.getId(), subscription.getStatus());
        
        // Log the subscription creation in Stripe
        logStripeSubscriptionEvent(subscription, "CREATED");
        
        return subscription;
    }
    
    private void logStripeSubscriptionEvent(Subscription subscription, String eventType) {
        log.info("Stripe Subscription Event: {} | ID: {} | Status: {} | Customer: {} | Current Period: {} to {}", 
                eventType, 
                subscription.getId(), 
                subscription.getStatus(),
                subscription.getCustomer(),
                subscription.getCurrentPeriodStart() != null ? 
                    java.time.Instant.ofEpochSecond(subscription.getCurrentPeriodStart()) : "null",
                subscription.getCurrentPeriodEnd() != null ? 
                    java.time.Instant.ofEpochSecond(subscription.getCurrentPeriodEnd()) : "null"
        );
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
        
        // Log the cancellation
        logStripeSubscriptionEvent(subscription, "CANCELLED");
    }
    
    public Subscription getSubscription(String subscriptionId) throws StripeException {
        log.info("Retrieving Stripe subscription: {}", subscriptionId);
        Subscription subscription = Subscription.retrieve(subscriptionId);
        log.info("Retrieved subscription: {} with status: {}", subscriptionId, subscription.getStatus());
        return subscription;
    }
    
    public Subscription updateSubscription(String subscriptionId, String newPriceId) throws StripeException {
        log.info("Updating Stripe subscription: {} to price: {}", subscriptionId, newPriceId);
        
        Subscription subscription = Subscription.retrieve(subscriptionId);
        
        com.stripe.param.SubscriptionUpdateParams params = com.stripe.param.SubscriptionUpdateParams.builder()
                .addItem(
                        com.stripe.param.SubscriptionUpdateParams.Item.builder()
                                .setId(subscription.getItems().getData().get(0).getId())
                                .setPrice(newPriceId)
                                .build()
                )
                .build();
        
        Subscription updatedSubscription = subscription.update(params);
        log.info("Updated Stripe subscription: {}", subscriptionId);
        
        logStripeSubscriptionEvent(updatedSubscription, "UPDATED");
        return updatedSubscription;
    }
    
    public SetupIntent createSetupIntent(String customerId, UUID userId) throws StripeException {
        log.info("Creating setup intent for customer: {}", customerId);
        
        Map<String, String> metadata = new HashMap<>();
        metadata.put("userId", userId.toString());
        metadata.put("purpose", "subscription_setup");
        
        SetupIntentCreateParams params = SetupIntentCreateParams.builder()
                .setCustomer(customerId)
                .putAllMetadata(metadata)
                .addPaymentMethodType("card")
                .setUsage(SetupIntentCreateParams.Usage.OFF_SESSION)
                .build();
        
        SetupIntent setupIntent = SetupIntent.create(params);
        log.info("Created setup intent: {}", setupIntent.getId());
        
        return setupIntent;
    }
    
    public void ensureCustomerHasPaymentMethod(String customerId) throws StripeException {
        log.info("🔍 Checking payment methods for customer: {}", customerId);
        
        // Check if customer has any payment methods
        com.stripe.param.PaymentMethodListParams params = com.stripe.param.PaymentMethodListParams.builder()
                .setCustomer(customerId)
                .setType(com.stripe.param.PaymentMethodListParams.Type.CARD)
                .build();
        
        com.stripe.model.PaymentMethodCollection paymentMethods = com.stripe.model.PaymentMethod.list(params);
        
        if (paymentMethods.getData().isEmpty()) {
            log.error("❌ No payment methods found for customer: {}", customerId);
            throw new RuntimeException("Customer has no payment methods attached. Please complete payment method setup first. " +
                    "The setup intent must be confirmed on the frontend before calling the complete endpoint.");
        }
        
        // Check if customer has a default payment method
        Customer customer = Customer.retrieve(customerId);
        if (customer.getInvoiceSettings() == null || 
            customer.getInvoiceSettings().getDefaultPaymentMethod() == null) {
            
            // Set the first payment method as default
            com.stripe.model.PaymentMethod firstPaymentMethod = paymentMethods.getData().get(0);
            log.info("💳 Setting default payment method: {} for customer: {}", 
                    firstPaymentMethod.getId(), customerId);
            
            CustomerUpdateParams updateParams = CustomerUpdateParams.builder()
                    .setInvoiceSettings(
                            CustomerUpdateParams.InvoiceSettings.builder()
                                    .setDefaultPaymentMethod(firstPaymentMethod.getId())
                                    .build()
                    )
                    .build();
            
            customer.update(updateParams);
            log.info("✅ Default payment method set for customer: {}", customerId);
        } else {
            log.info("✅ Customer already has default payment method: {}", 
                    customer.getInvoiceSettings().getDefaultPaymentMethod());
        }
    }
    
    public com.stripe.model.InvoiceCollection getBillingHistory(String customerId, int limit) throws StripeException {
        log.info("Fetching billing history for customer: {}", customerId);
        
        var params = com.stripe.param.InvoiceListParams.builder()
                .setCustomer(customerId)
                .setLimit((long) limit)
                .addAllExpand(java.util.Arrays.asList("data.payment_intent", "data.subscription"))
                .build();
        
        return com.stripe.model.Invoice.list(params);
    }
    
    public com.stripe.model.PaymentMethodCollection getPaymentMethods(String customerId) throws StripeException {
        log.info("Fetching payment methods for customer: {}", customerId);
        
        var params = com.stripe.param.PaymentMethodListParams.builder()
                .setCustomer(customerId)
                .setType(com.stripe.param.PaymentMethodListParams.Type.CARD)
                .build();
        
        return com.stripe.model.PaymentMethod.list(params);
    }
    
    public SetupIntent createSetupIntent(String customerId) throws StripeException {
        log.info("Creating setup intent for customer: {}", customerId);
        
        SetupIntentCreateParams params = SetupIntentCreateParams.builder()
                .setCustomer(customerId)
                .addPaymentMethodType("card")
                .setUsage(SetupIntentCreateParams.Usage.OFF_SESSION)
                .build();
        
        return SetupIntent.create(params);
    }
    
    public void attachPaymentMethod(String paymentMethodId, String customerId) throws StripeException {
        log.info("Attaching payment method {} to customer: {}", paymentMethodId, customerId);
        
        com.stripe.model.PaymentMethod paymentMethod = com.stripe.model.PaymentMethod.retrieve(paymentMethodId);
        
        var params = com.stripe.param.PaymentMethodAttachParams.builder()
                .setCustomer(customerId)
                .build();
        
        paymentMethod.attach(params);
    }
    
    public void setDefaultPaymentMethod(String customerId, String paymentMethodId) throws StripeException {
        log.info("Setting default payment method {} for customer: {}", paymentMethodId, customerId);
        
        Customer customer = Customer.retrieve(customerId);
        
        CustomerUpdateParams updateParams = CustomerUpdateParams.builder()
                .setInvoiceSettings(
                        CustomerUpdateParams.InvoiceSettings.builder()
                                .setDefaultPaymentMethod(paymentMethodId)
                                .build()
                )
                .build();
        
        customer.update(updateParams);
    }
    
    public void detachPaymentMethod(String paymentMethodId) throws StripeException {
        log.info("Detaching payment method: {}", paymentMethodId);
        
        com.stripe.model.PaymentMethod paymentMethod = com.stripe.model.PaymentMethod.retrieve(paymentMethodId);
        paymentMethod.detach();
    }
}

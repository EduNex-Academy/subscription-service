# Stripe-First Subscription Implementation

## 🎯 Core Principle: Stripe is the Source of Truth

Your subscription system now follows a **Stripe-first approach** where:
- ✅ **Stripe handles all subscription logic** (billing, renewals, status changes)
- ✅ **Database serves as a log/mirror** of Stripe state for easy queries
- ✅ **Webhooks sync Stripe state** to your database automatically
- ✅ **No manual activation** - everything driven by Stripe events

## 🏗️ Architecture Flow

```
Frontend → Backend → Stripe → Webhooks → Database Sync
    ↑                                         ↓
    └─────── Query Database for UI ←─────────┘
```

### 1. Subscription Creation Flow

```
1. User clicks "Subscribe" in frontend
2. Backend creates Stripe customer (with duplicate prevention)
3. Backend creates Stripe subscription with your price ID
4. Backend creates database record mirroring Stripe subscription
5. Frontend gets payment intent client_secret
6. User completes payment in frontend
7. Stripe sends webhook events
8. Webhooks sync subscription status to database
9. Points awarded automatically via webhooks
```

### 2. Key Webhook Events (Auto-handled)

- `customer.subscription.created` → Log new subscription
- `customer.subscription.updated` → Sync status changes
- `payment_intent.succeeded` → Award points for initial payment
- `invoice.payment_succeeded` → Handle renewals & award renewal points
- `invoice.payment_failed` → Handle failed renewals
- `customer.subscription.deleted` → Handle cancellations

## 🛠️ Implementation Details

### Customer Duplicate Prevention
```java
// First check by userId metadata, then by email
Customer existingCustomer = findCustomerByUserId(userId);
if (existingCustomer != null) {
    return existingCustomer; // Reuse existing
}
```

### Stripe-First Subscription Creation
```java
// Create subscription in Stripe with automatic payment collection
SubscriptionCreateParams params = SubscriptionCreateParams.builder()
    .setCustomer(customerId)
    .addItem(item)
    .setCollectionMethod(CHARGE_AUTOMATICALLY) // Let Stripe handle billing
    .setPaymentSettings(saveCard) // Save for renewals
    .build();
```

### Database as Mirror
```java
// Database record reflects Stripe state
userSubscription.setStatus(mapStripeStatusToLocal(stripeSubscription.getStatus()));
userSubscription.setStartDate(fromStripeTimestamp(stripeSubscription.getCurrentPeriodStart()));
userSubscription.setEndDate(fromStripeTimestamp(stripeSubscription.getCurrentPeriodEnd()));
```

### Real-time Sync via Webhooks
```java
private void syncSubscriptionToDatabase(Subscription stripeSubscription, String reason) {
    // Find or create database record
    UserSubscription dbSubscription = findOrCreateFromStripe(stripeSubscription);
    
    // Update to match Stripe exactly
    updateSubscriptionFromStripe(dbSubscription, stripeSubscription);
    
    log.info("🔄 Synced subscription from Stripe: {}", reason);
}
```

## 🎁 Automatic Points System

### Initial Subscription Points
```java
// Awarded when subscription becomes active
if ("active".equals(stripeSubscription.getStatus())) {
    awardPointsForSubscription(subscription, "NEW_SUBSCRIPTION");
}
```

### Renewal Points
```java
// Awarded on successful invoice payment (renewal)
private void handleInvoicePaymentSucceeded(Event event) {
    // Award renewal points automatically
    pointsService.awardPoints(userId, plan.getPointsAwarded(), 
        "Subscription renewed", "SUBSCRIPTION_RENEWAL", subscriptionId);
}
```

## 🔄 Automatic Renewals

Stripe handles renewals completely:

1. **Stripe automatically charges** the saved payment method on renewal date
2. **Success**: `invoice.payment_succeeded` webhook → extends subscription, awards points
3. **Failure**: `invoice.payment_failed` webhook → marks as past due
4. **Retry logic**: Stripe handles retry attempts automatically
5. **Final failure**: Subscription cancelled automatically by Stripe

## 📊 Database Schema (Logging Purpose)

```sql
user_subscriptions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    plan_id UUID REFERENCES subscription_plans(id),
    
    -- Stripe Integration
    stripe_subscription_id VARCHAR UNIQUE, -- Links to Stripe
    stripe_customer_id VARCHAR,
    
    -- Mirror of Stripe State
    status ENUM('PENDING', 'ACTIVE', 'CANCELLED', 'EXPIRED'),
    start_date TIMESTAMP,
    end_date TIMESTAMP,
    auto_renew BOOLEAN,
    
    -- Audit Fields
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);
```

## 🧪 Testing Your Implementation

### 1. Test Subscription Creation
```bash
# Create subscription via your API
POST /api/v1/subscriptions/create
{
    "planId": "your-plan-id",
    "email": "test@example.com"
}

# Expected: Subscription created in Stripe, database record mirrors it
```

### 2. Test Payment Completion
```javascript
// In frontend, complete payment
const {error} = await stripe.confirmPayment({
    elements,
    confirmParams: {
        return_url: 'http://localhost:3000/success'
    }
});

// Expected: Webhook activates subscription, points awarded
```

### 3. Verify Renewal (Use Stripe Test Clock)
```bash
# In Stripe Dashboard:
# 1. Create test clock
# 2. Advance time by 1 month
# 3. Check webhook logs

# Expected: Renewal processed, points awarded, subscription extended
```

## 📋 Benefits of This Approach

✅ **No duplicate customers** - Smart lookup prevents duplicates  
✅ **Automatic renewals** - Stripe handles everything  
✅ **Card security** - Stripe stores payment methods securely  
✅ **Automatic retries** - Stripe retries failed payments  
✅ **Real-time sync** - Webhooks keep database current  
✅ **Compliance** - Stripe handles PCI compliance  
✅ **Global payments** - Stripe supports worldwide payments  
✅ **Dunning management** - Stripe handles failed payment recovery  
✅ **Proration** - Stripe handles plan changes automatically  
✅ **Tax calculation** - Stripe can handle tax calculation  

## 🚨 Important Notes

1. **Never manually change subscription status** - let Stripe webhooks handle it
2. **Database is for queries only** - don't use it for business logic
3. **Always verify with Stripe** for critical operations
4. **Webhook endpoints must be idempotent** - Stripe may send duplicates
5. **Test with Stripe test clocks** for time-based scenarios

## 🔧 Troubleshooting

### Webhook Not Received
```bash
# Check Stripe webhook logs in dashboard
# Verify webhook endpoint URL is accessible
# Check webhook signing secret configuration
```

### Subscription Not Activating
```bash
# Check payment intent status in Stripe
# Verify webhook event was processed
# Check application logs for errors
```

### Points Not Awarded
```bash
# Check if subscription has associated plan
# Verify points service is working
# Check webhook processing logs
```

Your subscription service is now truly **Stripe-powered** with your database serving as an efficient query layer! 🎉

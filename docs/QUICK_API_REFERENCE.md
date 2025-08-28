# Quick API Reference for Frontend Integration

## Authentication Required Endpoints

All endpoints except subscription plans require `Authorization: Bearer <token>` header.

## API Endpoints Summary

### 🔓 Subscription Plans (No Auth Required)
```
GET  /api/v1/subscription-plans                    # Get all plans
GET  /api/v1/subscription-plans/{planId}           # Get plan by ID
GET  /api/v1/subscription-plans/by-name/{name}     # Get plans by name
```

### 🔒 Subscriptions (Auth Required)
```
POST /api/v1/subscriptions/setup                   # Step 1: Setup subscription
POST /api/v1/subscriptions/complete               # Step 2: Complete subscription
POST /api/v1/subscriptions/{id}/cancel            # Cancel subscription
POST /api/v1/subscriptions/confirm                # Manual confirmation
POST /api/v1/subscriptions/cleanup                # Fix incomplete subscriptions
GET  /api/v1/subscriptions/user                   # Get user subscriptions
GET  /api/v1/subscriptions/user/active            # Get active subscription
```

### 🔒 Points Management (Auth Required)
```
GET  /api/v1/points/wallet                        # Get points balance
POST /api/v1/points/redeem                        # Redeem points
GET  /api/v1/points/transactions                  # Get transaction history
```

## Quick Implementation Steps

### 1. Setup API Client
```javascript
const API_BASE = 'http://localhost:8080/api/v1';
const authToken = localStorage.getItem('authToken');

const apiCall = async (endpoint, options = {}) => {
  const response = await fetch(`${API_BASE}${endpoint}`, {
    headers: {
      'Content-Type': 'application/json',
      ...(authToken && { 'Authorization': `Bearer ${authToken}` })
    },
    ...options
  });
  return response.json();
};
```

### 2. Load Subscription Plans
```javascript
const plans = await apiCall('/subscription-plans');
// Returns: Array of {planId, planName, price, description, pointsValue, billingInterval}
```

### 3. Create Subscription (2-Step Process)
```javascript
// Step 1: Setup
const setup = await apiCall('/subscriptions/setup', {
  method: 'POST',
  body: JSON.stringify({ planId: 'selected-plan-id', email: 'user@email.com' })
});
// Returns: {setupIntentId, clientSecret, customerId, planId}

// Step 2: Confirm with Stripe (frontend)
const stripe = Stripe('pk_test_...');
await stripe.confirmSetupIntent(setup.clientSecret, {
  payment_method: { card: cardElement }
});

// Step 3: Complete subscription
const subscription = await apiCall(`/subscriptions/complete?customerId=${setup.customerId}&planId=${setup.planId}`, {
  method: 'POST'
});
// Returns: UserSubscriptionDto with subscription details
```

### 4. Get User Data
```javascript
// Get active subscription
const activeSubscription = await apiCall('/subscriptions/user/active');

// Get points wallet
const wallet = await apiCall('/points/wallet');
// Returns: {currentBalance, totalEarned, totalRedeemed, userId}

// Get all subscriptions
const allSubscriptions = await apiCall('/subscriptions/user');
```

### 5. Points Management
```javascript
// Redeem points
await apiCall('/points/redeem', {
  method: 'POST',
  body: JSON.stringify({
    pointsToRedeem: 100,
    description: 'Course material purchase'
  })
});

// Get transaction history
const transactions = await apiCall('/points/transactions?page=0&size=20');
// Returns: {content: [...], totalElements, totalPages, number, size}
```

### 6. Troubleshooting
```javascript
// Fix incomplete subscriptions (if payments stuck)
const results = await apiCall('/subscriptions/cleanup', { method: 'POST' });
// Processes all incomplete subscriptions and activates them

// Manual confirmation for specific subscription
await apiCall(`/subscriptions/confirm?stripeSubscriptionId=sub_xxxxx`, { method: 'POST' });
```

## Common Response Formats

### Success Response
```json
{
  "success": true,
  "message": "Operation completed successfully",
  "data": { ... },
  "timestamp": "2025-08-26T10:30:00Z"
}
```

### Error Response
```json
{
  "success": false,
  "message": "Error description",
  "data": null,
  "timestamp": "2025-08-26T10:30:00Z"
}
```

## Data Transfer Objects (DTOs)

### SubscriptionPlanDto
```json
{
  "planId": "uuid",
  "planName": "Premium",
  "description": "Premium subscription with full access",
  "price": 29.99,
  "currency": "USD",
  "billingInterval": "MONTHLY",
  "pointsValue": 500,
  "stripePriceId": "price_xxxxx",
  "active": true
}
```

### UserSubscriptionDto
```json
{
  "subscriptionId": "uuid",
  "userId": "uuid", 
  "planId": "uuid",
  "planName": "Premium",
  "status": "ACTIVE",
  "startDate": "2025-08-26T10:00:00Z",
  "endDate": "2025-09-26T10:00:00Z",
  "stripeSubscriptionId": "sub_xxxxx",
  "stripeCustomerId": "cus_xxxxx"
}
```

### UserPointsWalletDto
```json
{
  "walletId": "uuid",
  "userId": "uuid",
  "currentBalance": 1500,
  "totalEarned": 2000,
  "totalRedeemed": 500,
  "lastUpdated": "2025-08-26T10:00:00Z"
}
```

### PointsTransactionDto
```json
{
  "transactionId": "uuid",
  "userId": "uuid",
  "transactionType": "EARNED",
  "pointsAmount": 500,
  "description": "Subscription activation bonus",
  "transactionDate": "2025-08-26T10:00:00Z",
  "relatedSubscriptionId": "uuid"
}
```

## Environment Variables Needed

```javascript
const config = {
  API_BASE_URL: 'http://localhost:8080/api/v1',
  STRIPE_PUBLIC_KEY: 'pk_test_your_stripe_public_key_here'
};
```

## Error Handling Tips

1. **401 Unauthorized**: Token expired, redirect to login
2. **429 Too Many Requests**: Subscription setup in progress, wait 30 seconds
3. **400 Bad Request**: Check request body format and required fields
4. **500 Internal Server Error**: Backend issue, show generic error message

## Testing Checklist

- [ ] Load subscription plans without authentication
- [ ] Authenticate and load user subscription data
- [ ] Create new subscription with valid payment method
- [ ] Verify points are awarded after subscription creation
- [ ] Test subscription cancellation
- [ ] Test points redemption
- [ ] Test cleanup for incomplete subscriptions
- [ ] Handle network errors and auth failures gracefully

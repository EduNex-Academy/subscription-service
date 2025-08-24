# Payment Gateway Integration Guide

## Stripe Webhook Setup

### 1. Get Your Webhook Secret from Stripe Dashboard

1. Go to [Stripe Dashboard](https://dashboard.stripe.com)
2. Navigate to **Developers → Webhooks**
3. Click **"Add endpoint"**
4. Set endpoint URL: `https://yourdomain.com/api/v1/webhooks/stripe`
5. Select these events:
   - `payment_intent.succeeded`
   - `payment_intent.payment_failed`
   - `invoice.payment_succeeded`
   - `invoice.payment_failed`
   - `customer.subscription.created`
   - `customer.subscription.updated`
   - `customer.subscription.deleted`
6. Click **"Add endpoint"**
7. Click on the created endpoint and reveal the **"Signing secret"**
8. Copy the webhook secret (starts with `whsec_`)

### 2. Set Environment Variable

Set the webhook secret in your environment:
```bash
set STRIPE_WEBHOOK_SECRET=whsec_your_actual_webhook_secret_here
```

## Frontend Integration Example (JavaScript)

### 1. Create Subscription Payment

```javascript
// Create subscription
const createSubscription = async (planId, email) => {
  const response = await fetch('/api/v1/subscriptions/create', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${authToken}`
    },
    body: JSON.stringify({
      planId: planId,
      billingCycle: 'MONTHLY', // or 'YEARLY'
      email: email
    })
  });
  
  const result = await response.json();
  return result.data; // Contains clientSecret and paymentIntentId
};

// Confirm payment with Stripe
const confirmPayment = async (clientSecret, paymentMethodId) => {
  const stripe = Stripe('pk_test_51RxrJYKFa3CiRyg4XkkcUhqYGys4gRHuBUuzmkECaRzyG64yCjt6XkGyGjmFGv2JzbGMAaa5pZaKjCNiD2XuzESq00yH9UZwGF');
  
  const result = await stripe.confirmPayment({
    clientSecret: clientSecret,
    payment_method: paymentMethodId,
    confirmation_method: 'manual'
  });
  
  if (result.error) {
    console.error('Payment failed:', result.error);
    return false;
  }
  
  return true;
};
```

### 2. Create One-time Payment Intent

```javascript
const createPaymentIntent = async (amount, currency, email) => {
  const response = await fetch('/api/v1/payments/create-intent', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${authToken}`
    },
    body: JSON.stringify({
      amount: amount,
      currency: currency,
      email: email
    })
  });
  
  const result = await response.json();
  return result.data;
};
```

## Testing the Payment Gateway

### 1. Test Credit Card Numbers (Stripe Test Mode)

- **Success:** 4242424242424242
- **Decline:** 4000000000000002
- **Insufficient Funds:** 4000000000009995
- **Expired Card:** 4000000000000069

### 2. API Endpoints Available

- `POST /api/v1/subscriptions/create` - Create subscription with payment
- `POST /api/v1/payments/create-intent` - Create payment intent
- `POST /api/v1/payments/confirm` - Confirm payment
- `POST /api/v1/webhooks/stripe` - Stripe webhook handler
- `GET /api/v1/subscriptions` - Get user subscriptions
- `POST /api/v1/subscriptions/{id}/cancel` - Cancel subscription

### 3. Environment Setup

Make sure to set these environment variables:
```bash
STRIPE_WEBHOOK_SECRET=whsec_your_webhook_secret_here
```

## Payment Flow

1. **Frontend** creates subscription/payment intent
2. **Backend** creates Stripe customer and payment intent
3. **Frontend** collects payment details and confirms with Stripe
4. **Stripe** processes payment and sends webhook to backend
5. **Backend** webhook handler updates payment status and activates subscription
6. **Frontend** receives confirmation and updates UI

## Security Notes

- Never expose secret keys in frontend code
- Always validate webhook signatures
- Use HTTPS in production
- Store webhook secrets as environment variables

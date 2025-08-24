# Stripe CLI Local Development Setup

## Installation (Windows)

### Method 1: Using Scoop (Recommended)
```bash
# Install Scoop if you don't have it
Set-ExecutionPolicy RemoteSigned -scope CurrentUser
iwr -useb get.scoop.sh | iex

# Install Stripe CLI
scoop bucket add stripe https://github.com/stripe/scoop-stripe-cli.git
scoop install stripe
```

### Method 2: Direct Download
1. Go to https://github.com/stripe/stripe-cli/releases/latest
2. Download `stripe_X.X.X_windows_x86_64.zip`
3. Extract and add to your PATH

### Method 3: Using Chocolatey
```bash
choco install stripe-cli
```

## Setup and Authentication

1. **Login to Stripe CLI:**
```bash
stripe login
```
This will open your browser to authenticate with your Stripe account.

2. **Verify installation:**
```bash
stripe --version
```

## Local Webhook Testing

### 1. Forward webhooks to local development server:
```bash
stripe listen --forward-to localhost:8083/api/v1/webhooks/stripe
```

This command will:
- Start listening for webhook events from your Stripe account
- Forward them to your local development server
- Display the webhook signing secret you need

### 2. Copy the webhook signing secret displayed in the output
It will look like: `whsec_1234567890abcdef...`

### 3. Set the webhook secret as environment variable:
```bash
set STRIPE_WEBHOOK_SECRET=whsec_your_webhook_signing_secret_from_cli
```

## Testing Commands

### Create test customers:
```bash
stripe customers create --email="test@example.com" --name="Test Customer"
```

### Create test payment intents:
```bash
stripe payment_intents create --amount=2000 --currency=usd --automatic-payment-methods[enabled]=true
```

### Trigger test webhook events:
```bash
stripe trigger payment_intent.succeeded
stripe trigger customer.subscription.created
stripe trigger invoice.payment_succeeded
```

### Listen to specific events only:
```bash
stripe listen --events=payment_intent.succeeded,customer.subscription.created --forward-to localhost:8083/api/v1/webhooks/stripe
```

## Development Workflow

1. Start your Spring Boot application: `./gradlew bootRun`
2. In another terminal, start Stripe CLI: `stripe listen --forward-to localhost:8083/api/v1/webhooks/stripe`
3. Copy the webhook secret and set it as environment variable
4. Test your payment flows using test card numbers
5. Monitor webhook events in both terminals

## Test Card Numbers

- **Success:** 4242424242424242
- **Decline:** 4000000000000002
- **Insufficient Funds:** 4000000000009995
- **Expired Card:** 4000000000000069
- **Processing Error:** 4000000000000119

## Useful CLI Commands

```bash
# View recent events
stripe events list

# Get specific event details
stripe events retrieve evt_1234567890

# Test webhook endpoints
stripe listen --print-json

# Forward to different port
stripe listen --forward-to localhost:3000/webhooks

# Listen with specific API version
stripe listen --api-version 2023-10-16
```

# Subscription Service - Complete Implementation

## Overview
This service manages user subscriptions, payments, points system, and instructor payouts for the learning platform.

## Key Features

### ✅ Fixed Issues
1. **Webhook JSON Handling** - Now properly handles Stripe's JSON webhook events
2. **Stripe-Managed Renewals** - Removed manual expiration calculation, Stripe handles all subscription lifecycle
3. **Automatic Points on Renewal** - Points automatically awarded when subscription renews
4. **Clean Architecture** - Removed unnecessary code and simplified webhook processing

### 🎯 Core Functionality

#### 1. Subscription Management
- Create subscriptions via Stripe
- Automatic renewal handling
- Status synchronization with Stripe
- Cancel and update subscriptions

#### 2. Points System
- Award points on subscription purchase
- Award points on subscription renewal
- Validate points before course access
- Deduct points for course resources (modules, quizzes)
- Transaction history tracking

#### 3. Instructor Payouts
- Track instructor earnings
- Create payout batches
- Process payments via Stripe
- Earnings history and reporting

#### 4. Payment Processing
- Stripe payment integration
- Secure webhook verification
- Payment status tracking

## Architecture

```
Student Subscribes → Stripe → Webhook → Points Awarded
Student Renews → Stripe Auto-Renewal → Webhook → Points Added
Student Accesses Course → Course Service → Validate Points → Deduct Points
```

## API Endpoints

### Subscription Management
- `POST /api/v1/subscriptions/create` - Create new subscription
- `GET /api/v1/subscriptions/user` - Get user's subscription
- `POST /api/v1/subscriptions/cancel` - Cancel subscription

### Points System (For Students)
- `GET /api/v1/points/wallet` - Get user's points wallet
- `GET /api/v1/points/balance` - Get current balance
- `GET /api/v1/points/transactions` - Get transaction history

### Points System (For Course Service)
- `POST /api/v1/points/validate` - Check if user has enough points
- `POST /api/v1/points/deduct` - Deduct points for resource access
- `POST /api/v1/points/award` - Award points (admin only)

### Instructor Payouts
- `GET /api/v1/instructor/payouts/earnings` - Get earnings history
- `GET /api/v1/instructor/payouts/pending` - Get pending earnings
- `GET /api/v1/instructor/payouts` - Get payout history
- `POST /api/v1/instructor/payouts/record-earning` - Record earning (admin)
- `POST /api/v1/instructor/payouts/create` - Create payout (admin)

### Webhooks
- `POST /api/v1/webhooks/stripe` - Stripe webhook handler

## Database Schema

### Core Tables
- `subscription_plans` - Available subscription plans
- `user_subscriptions` - User subscription records (synced with Stripe)
- `user_points_wallet` - User points balances
- `points_transactions` - Points transaction history
- `instructor_earnings` - Individual instructor earning records
- `instructor_payouts` - Payout batches for instructors
- `payments` - Payment records

## Environment Variables

```properties
# Database
DB_URL=jdbc:postgresql://localhost:5432/subscription_db
DB_USERNAME=your_username
DB_PASSWORD=your_password

# Stripe
STRIPE_SECRET_KEY=sk_test_...
STRIPE_WEBHOOK_SECRET=whsec_...

# Server
SERVER_PORT=9083
```

## Setup Instructions

### 1. Database Setup
```bash
# Run the init.sql to create initial schema
psql -U postgres -d subscription_db -f init.sql

# Run migration for instructor payouts
psql -U postgres -d subscription_db -f src/main/resources/db/migration/V2__Add_Instructor_Payouts.sql
```

### 2. Stripe Configuration

#### Create Products and Prices
```bash
# Monthly Plan
stripe products create --name="Monthly Subscription" --description="Monthly access with 100 points"
stripe prices create --product=prod_xxx --unit-amount=999 --currency=usd --recurring[interval]=month

# Yearly Plan
stripe products create --name="Yearly Subscription" --description="Yearly access with 1200 points"
stripe prices create --product=prod_xxx --unit-amount=9999 --currency=usd --recurring[interval]=year
```

#### Configure Webhook
```bash
# Install Stripe CLI
stripe listen --forward-to localhost:9083/api/v1/webhooks/stripe

# In production, configure webhook in Stripe Dashboard:
# Endpoint: https://yourdomain.com/api/v1/webhooks/stripe
# Events to listen: 
#   - customer.subscription.created
#   - customer.subscription.updated
#   - customer.subscription.deleted
#   - invoice.payment_succeeded
#   - invoice.payment_failed
```

### 3. Build and Run
```bash
# Build
./gradlew clean build

# Run
./gradlew bootRun

# Or with Docker
docker-compose up --build
```

## Testing

### Test Webhook Locally
```bash
# Terminal 1: Run the service
./gradlew bootRun

# Terminal 2: Forward Stripe events
stripe listen --forward-to localhost:9083/api/v1/webhooks/stripe

# Terminal 3: Trigger test events
stripe trigger customer.subscription.created
stripe trigger invoice.payment_succeeded
```

### Test Points System
```bash
# Create subscription (use Swagger UI or Postman)
POST http://localhost:9083/api/v1/subscriptions/create

# Check points balance
GET http://localhost:9083/api/v1/points/balance

# Validate points (from course service)
POST http://localhost:9083/api/v1/points/validate?userId=xxx&requiredPoints=10

# Deduct points (from course service)
POST http://localhost:9083/api/v1/points/deduct?userId=xxx&points=10&resourceType=COURSE_MODULE&resourceId=yyy&description=Module%20Access
```

## Integration with Course Service

See [COURSE_SERVICE_INTEGRATION.md](docs/COURSE_SERVICE_INTEGRATION.md) for detailed integration guide.

**Key Integration Points:**
1. **Before granting course access** → Call `/validate` endpoint
2. **When granting access** → Call `/deduct` endpoint
3. **Display user balance** → Call `/balance` endpoint

## Subscription Flow

### New Subscription
1. User selects a plan in frontend
2. Frontend calls `POST /api/v1/subscriptions/create`
3. Backend creates Stripe subscription
4. Stripe webhook `customer.subscription.created` fires
5. Stripe webhook `customer.subscription.updated` fires (when active)
6. Points awarded to user wallet
7. User can now access courses

### Renewal (Automatic)
1. Stripe automatically charges user
2. `invoice.payment_succeeded` webhook fires
3. Subscription dates updated from Stripe
4. Points automatically added to wallet
5. User continues accessing content

### Points Usage
1. User clicks on course module
2. Course service validates points
3. If sufficient, points deducted
4. Access granted
5. Transaction recorded

## Points Configuration

Edit in `subscription_plans` table:

```sql
-- Example: Set points for plans
UPDATE subscription_plans 
SET points_awarded = 100 
WHERE billing_cycle = 'MONTHLY';

UPDATE subscription_plans 
SET points_awarded = 1200 
WHERE billing_cycle = 'YEARLY';
```

## Instructor Payout System

### How It Works
1. Instructors earn money when students access their content
2. Earnings recorded via `record-earning` endpoint
3. Admin creates payout batches periodically (e.g., monthly)
4. Payouts processed via Stripe transfers
5. Status tracked throughout process

### Creating Payouts (Admin)
```bash
# Record earnings (automated or manual)
POST /api/v1/instructor/payouts/record-earning

# Create monthly payout
POST /api/v1/instructor/payouts/create?instructorId=xxx&periodStart=2025-10-01T00:00:00&periodEnd=2025-10-31T23:59:59

# Process with Stripe
POST /api/v1/instructor/payouts/{id}/process?stripePayoutId=po_xxx

# Mark as complete
POST /api/v1/instructor/payouts/{id}/complete
```

## API Documentation

Swagger UI available at: `http://localhost:9083/swagger-ui.html`

## Security

- All endpoints require JWT authentication
- Role-based access control (STUDENT, INSTRUCTOR, ADMIN)
- Stripe webhook signature verification
- Secure payment processing

## Monitoring

- Actuator endpoints: `/actuator/health`, `/actuator/metrics`
- Comprehensive logging with SLF4J
- Transaction tracking for all points operations

## Troubleshooting

### Webhook Not Working
```bash
# Check webhook secret is correct
# Verify endpoint is publicly accessible
# Check logs for signature verification errors
tail -f logs/subscription-service.log | grep webhook
```

### Points Not Awarded
```bash
# Check subscription status in database
SELECT * FROM user_subscriptions WHERE user_id = 'xxx';

# Check points wallet
SELECT * FROM user_points_wallet WHERE user_id = 'xxx';

# Check transactions
SELECT * FROM points_transactions WHERE user_id = 'xxx' ORDER BY created_at DESC;
```

### Subscription Not Renewing
- Stripe handles renewals automatically
- Check Stripe dashboard for subscription status
- Verify webhook events are being received
- Check payment method is valid

## Production Checklist

- [ ] Set production Stripe keys
- [ ] Configure webhook endpoint in Stripe Dashboard
- [ ] Set up database backups
- [ ] Configure monitoring and alerting
- [ ] Set up SSL/TLS certificates
- [ ] Configure proper logging rotation
- [ ] Test failover scenarios
- [ ] Document runbooks for common issues

## Support

For integration questions, see documentation in `/docs` folder:
- `COURSE_SERVICE_INTEGRATION.md` - Course service integration guide
- `FRONTEND_INTEGRATION_GUIDE.md` - Frontend integration guide
- `STRIPE_CLI_SETUP.md` - Stripe CLI setup

## License

Proprietary - CS3203 Software Engineering Project


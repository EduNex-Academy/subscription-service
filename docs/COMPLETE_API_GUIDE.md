# Subscription Service API - Complete Guide

## Overview
This subscription service provides a complete billing, subscription, and points-based reward system with instructor payout capabilities. Students purchase subscriptions to earn points that they can use to access course content.

## Key Features

### 🔔 Subscription Management
- **Stripe Integration**: Seamless payment processing with webhooks
- **Automatic Renewals**: Stripe handles subscription lifecycle
- **Real-time Synchronization**: Webhook events keep our database in sync

### 🎁 Points & Rewards System
- **Automatic Point Awards**: Users earn points on subscription creation and renewal
- **Point-based Access**: Course modules and quizzes require points
- **Transaction History**: Complete audit trail of all point activities

### 💰 Instructor Payout System
- **Revenue Sharing**: 70% of subscription revenue allocated to instructors
- **Automated Tracking**: Earnings recorded on every subscription payment
- **Flexible Payouts**: Admin-controlled payout creation and processing

## API Endpoints

### Subscription Endpoints

#### Create Subscription
```http
POST /api/v1/subscriptions
Authorization: Bearer {token}
Content-Type: application/json

{
  "planId": "uuid",
  "paymentMethodId": "stripe_payment_method_id"
}
```

#### Get User Subscriptions
```http
GET /api/v1/subscriptions
Authorization: Bearer {token}
```

#### Cancel Subscription
```http
DELETE /api/v1/subscriptions/{subscriptionId}
Authorization: Bearer {token}
```

### Points System Endpoints

#### Get User Wallet
```http
GET /api/v1/points/wallet
Authorization: Bearer {token}
```

#### Get Points Balance
```http
GET /api/v1/points/balance
Authorization: Bearer {token}
```

#### Validate Points (Course Service Integration)
```http
POST /api/v1/points/validate?userId={uuid}&requiredPoints={amount}
Authorization: Bearer {token}
```

#### Deduct Points (Course Service Integration)
```http
POST /api/v1/points/deduct
Authorization: Bearer {token}

Query Parameters:
- userId: UUID
- points: Integer
- resourceType: String (e.g., "COURSE_MODULE", "QUIZ")
- resourceId: UUID
- description: String
```

#### Get Transaction History
```http
GET /api/v1/points/transactions?page=0&size=20
Authorization: Bearer {token}
```

### Instructor Payout Endpoints

#### Get Instructor Earnings
```http
GET /api/v1/instructor/payouts/earnings?page=0&size=20
Authorization: Bearer {token}
```

#### Get Pending Earnings
```http
GET /api/v1/instructor/payouts/pending
Authorization: Bearer {token}
```

#### Get Payout History
```http
GET /api/v1/instructor/payouts?page=0&size=20
Authorization: Bearer {token}
```

#### Admin: Record Earning
```http
POST /api/v1/instructor/payouts/record-earning
Authorization: Bearer {admin_token}

Query Parameters:
- instructorId: UUID
- courseId: UUID (optional)
- amount: BigDecimal
- earningType: COURSE_ENROLLMENT|SUBSCRIPTION_REVENUE_SHARE|BONUS|ADJUSTMENT
- description: String
```

#### Admin: Create Payout
```http
POST /api/v1/instructor/payouts/create
Authorization: Bearer {admin_token}

Query Parameters:
- instructorId: UUID
- periodStart: LocalDateTime (ISO format)
- periodEnd: LocalDateTime (ISO format)
```

### Webhook Endpoints

#### Stripe Webhook
```http
POST /api/v1/webhooks/stripe
Content-Type: application/json
Stripe-Signature: {stripe_signature}

{Stripe Event JSON}
```

## Course Service Integration

### Points Validation Flow

1. **Before Access**: Course service calls `/api/v1/points/validate`
2. **Grant Access**: If sufficient points, allow access
3. **Deduct Points**: After successful access, call `/api/v1/points/deduct`

### Example Integration Code

```java
// In Course Service
@Autowired
private SubscriptionServiceClient subscriptionClient;

public boolean allowCourseAccess(UUID userId, UUID moduleId, Integer requiredPoints) {
    // Check if user has enough points
    PointsValidationResponse validation = subscriptionClient.validatePoints(userId, requiredPoints);
    
    if (!validation.isHasEnoughPoints()) {
        throw new InsufficientPointsException("Need " + requiredPoints + " points, have " + validation.getAvailablePoints());
    }
    
    // Grant access and deduct points
    boolean deducted = subscriptionClient.deductPoints(
        userId, 
        requiredPoints, 
        "COURSE_MODULE", 
        moduleId,
        "Access to module: " + moduleId
    );
    
    return deducted;
}
```

## Subscription Flow

### New Subscription Process

1. **User Initiates**: Frontend calls create subscription endpoint
2. **Stripe Processing**: Subscription created in Stripe
3. **Webhook Notification**: Stripe sends webhook events
4. **Database Sync**: Our service updates subscription status
5. **Points Award**: Automatic point allocation based on plan
6. **Instructor Earnings**: Revenue share recorded for instructors

### Renewal Process

1. **Stripe Auto-Renewal**: Stripe automatically charges customer
2. **Invoice Payment Succeeded**: Webhook received
3. **Database Update**: Subscription dates updated from Stripe
4. **Points Award**: Additional points awarded for renewal
5. **Revenue Share**: Instructor earnings recorded

## Error Handling

### Common Error Responses

```json
{
  "success": false,
  "message": "Error description",
  "data": null,
  "timestamp": "2023-10-10T12:00:00Z"
}
```

### Error Codes

- `400 Bad Request`: Invalid request parameters
- `401 Unauthorized`: Missing or invalid authentication
- `403 Forbidden`: Insufficient permissions
- `404 Not Found`: Resource not found
- `500 Internal Server Error`: Server-side error

## Database Schema

### Key Tables

1. **user_subscriptions**: User subscription records
2. **subscription_plans**: Available subscription plans
3. **user_points_wallet**: User point balances
4. **points_transactions**: Point transaction history
5. **instructor_earnings**: Individual instructor earning records
6. **instructor_payouts**: Payout batch records

## Security

### Authentication
- Bearer token authentication
- Role-based access control (STUDENT, INSTRUCTOR, ADMIN)

### Webhook Security
- Stripe signature verification
- Secure endpoint protection

## Monitoring & Logging

### Key Metrics
- Subscription creation/renewal rates
- Point earning/spending patterns
- Instructor earning distributions
- Webhook processing success rates

### Log Levels
- **INFO**: Normal operations, webhook events
- **WARN**: Business logic warnings, validation failures
- **ERROR**: System errors, webhook failures

## Configuration

### Required Environment Variables

```properties
# Stripe Configuration
stripe.api.key=sk_test_...
stripe.webhook.secret=whsec_...

# Database Configuration
spring.datasource.url=jdbc:postgresql://localhost:5432/subscription_db
spring.datasource.username=subscription_user
spring.datasource.password=password

# Security Configuration
jwt.secret=your_jwt_secret
jwt.expiration=86400000
```

This comprehensive guide covers all aspects of the subscription service including the fixed webhook handling, complete points system, and instructor payout functionality.
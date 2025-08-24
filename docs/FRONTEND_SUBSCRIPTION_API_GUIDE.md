# Frontend Integration Guide - Subscription Service API

## Overview
This document provides comprehensive details about the Subscription Service API endpoints, request/response formats, authentication requirements, and integratio### 1. Create Subscription
**Endpoint:** `POST /api/v1/subscriptions/create`  
**Authentication:** Required (STUDENT/ADMIN)  
**Description:** Create a new subscription with payment processing through Stripe.

**Backend Flow:**

1. **Authentication**: Extracts user ID from JWT token
2. **Plan Validation**: Validates subscription plan exists and is active
3. **Payment Processing**: Creates Stripe payment intent for the subscription
4. **Subscription Entity**: Creates subscription record with PENDING status
5. **Database Transaction**: Saves subscription with proper relationships
6. **Points Calculation**: Calculates billing cycle and end date
7. **Response**: Returns payment intent details for frontend completion

```http
POST /api/v1/subscriptions/create
```

**Request Body:**

```json
{
  "planId": "550e8400-e29b-41d4-a716-446655440000",
  "billingCycle": "MONTHLY",
  "email": "user@example.com",
  "paymentMethodId": "pm_1234567890"
}ntend development.

**Base URL:** `http://localhost:8083`  
**API Version:** `v1`  
**Authentication:** Bearer Token (JWT from Keycloak)

## Table of Contents
1. [Authentication](#authentication)
2. [API Response Format](#api-response-format)
3. [Subscription Plans API](#subscription-plans-api)
4. [Payment Management API](#payment-management-api)
5. [Subscription Management API](#subscription-management-api)
6. [Points Management API](#points-management-api)
7. [Webhooks](#webhooks)
8. [Integration Flow Examples](#integration-flow-examples)
9. [Error Handling](#error-handling)

## Authentication

All endpoints except subscription plans require Bearer token authentication.

### Headers Required:
```http
Authorization: Bearer <JWT_TOKEN>
Content-Type: application/json
```

### Role-based Access:
- **STUDENT**: Can access their own data and create subscriptions
- **ADMIN**: Can access all endpoints and perform administrative operations

## API Response Format

All API responses follow a consistent format:

```json
{
  "success": true,
  "message": "Operation successful",
  "data": {}, // Response data (can be object, array, or null)
  "timestamp": "2025-08-23T10:30:45"
}
```

### Error Response:
```json
{
  "success": false,
  "message": "Error description",
  "data": null,
  "timestamp": "2025-08-23T10:30:45"
}
```

## Subscription Plans API

### 1. Get All Subscription Plans
**Endpoint:** `GET /api/v1/subscription-plans`  
**Authentication:** Not required (Public endpoint)  
**Description:** Retrieve all active subscription plans available for purchase.

**Backend Flow:**
1. **Controller Layer**: `SubscriptionPlanController.getAllPlans()` receives request without authentication requirements
2. **Service Layer**: Calls `subscriptionPlanService.getAllActivePlans()`
3. **Repository Layer**: Queries database with `WHERE isActive = true`
4. **Data Mapping**: Converts entities to DTOs using ModelMapper
5. **Response**: Returns standardized API response with list of plans

```http
GET /api/v1/subscription-plans
```

**Response:**
```json
{
  "success": true,
  "message": "Retrieved all subscription plans",
  "data": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "name": "Premium Monthly",
      "billingCycle": "MONTHLY",
      "price": 29.99,
      "currency": "USD",
      "pointsAwarded": 1000,
      "features": [
        "Unlimited access to premium courses",
        "1-on-1 mentoring sessions",
        "Certificate of completion",
        "Priority support"
      ],
      "isActive": true,
      "createdAt": "2025-01-15T10:30:45"
    }
  ],
  "timestamp": "2025-08-23T10:30:45"
}
```

### 2. Get Subscription Plan by ID
**Endpoint:** `GET /api/v1/subscription-plans/{planId}`  
**Authentication:** Not required (Public endpoint)  
**Description:** Retrieve specific subscription plan details by unique identifier.

**Backend Flow:**

1. **Path Variable Extraction**: Extracts UUID from path parameter
2. **Service Layer**: Calls `subscriptionPlanService.getPlanById(planId)`
3. **Repository Layer**: Performs `findById()` query
4. **Validation**: Checks if plan exists and is active
5. **Response**: Returns plan details or 404 if not found

```http
GET /api/v1/subscription-plans/{planId}
```

**Path Parameters:**

- `planId` (UUID): The subscription plan ID

**Response:** Single plan object (same structure as above)

### 3. Get Plans by Name
**Endpoint:** `GET /api/v1/subscription-plans/by-name/{planName}`  
**Authentication:** Not required (Public endpoint)  
**Description:** Search subscription plans by name pattern (useful for filtering).

**Backend Flow:**

1. **Path Parameter**: Extracts plan name from URL
2. **Service Layer**: Calls `subscriptionPlanService.getPlansByName(planName)`
3. **Repository Query**: Uses `LIKE` query for name matching
4. **Filtering**: Returns only active plans matching the name pattern
5. **Response**: Returns list of matching plans

```http
GET /api/v1/subscription-plans/by-name/{planName}
```

**Path Parameters:**
- `planName` (String): Plan name to search for

**Response:** Array of matching plans

### Data Types:
- **BillingCycle**: `"MONTHLY"` | `"YEARLY"`

## Payment Management API

### 1. Create Payment Intent
**Endpoint:** `POST /api/v1/payments/create-intent`  
**Authentication:** Required (STUDENT/ADMIN)  
**Description:** Create a Stripe payment intent for processing subscription payments.

**Backend Flow:**

1. **Authentication**: Validates JWT token and extracts user ID
2. **Request Validation**: Validates amount, currency, and email format
3. **Stripe Customer**: Creates or retrieves existing Stripe customer
4. **Payment Intent**: Creates Stripe payment intent with metadata
5. **Response**: Returns client secret for frontend Stripe integration

```http
POST /api/v1/payments/create-intent
```

**Request Body:**

```json
{
  "planId": "550e8400-e29b-41d4-a716-446655440000",
  "amount": 29.99,
  "currency": "USD",
  "email": "user@example.com",
  "paymentMethodId": "pm_1234567890" // Optional
}
```

**Response:**
```json
{
  "success": true,
  "message": "Payment intent created successfully",
  "data": {
    "clientSecret": "pi_1234567890_secret_abcdef",
    "paymentIntentId": "pi_1234567890",
    "status": "requires_payment_method"
  },
  "timestamp": "2025-08-23T10:30:45"
}
```

### 2. Confirm Payment
**Endpoint:** `POST /api/v1/payments/confirm`  
**Authentication:** Required (STUDENT/ADMIN)  
**Description:** Server-side payment confirmation endpoint (mostly handled client-side).

**Backend Flow:**

1. **Authentication**: Validates user token
2. **Payment Intent ID**: Validates the provided payment intent
3. **Confirmation Logic**: Handles any server-side confirmation requirements
4. **Event Logging**: Logs payment confirmation for audit trail
5. **Response**: Confirms payment initiation

```http
POST /api/v1/payments/confirm
```

**Request Body:**

```json
{
  "paymentIntentId": "pi_1234567890"
}
```

**Response:**
```json
{
  "success": true,
  "message": "Payment confirmation initiated",
  "data": "OK",
  "timestamp": "2025-08-23T10:30:45"
}
```

## Subscription Management API

### 1. Create Subscription
**Authentication required (STUDENT/ADMIN)**

```http
POST /api/v1/subscriptions/create
```

**Request Body:**
```json
{
  "planId": "550e8400-e29b-41d4-a716-446655440000",
  "billingCycle": "MONTHLY",
  "email": "user@example.com",
  "paymentMethodId": "pm_1234567890" // Optional
}
```

**Response:**
```json
{
  "success": true,
  "message": "Subscription created successfully",
  "data": {
    "clientSecret": "pi_1234567890_secret_abcdef",
    "paymentIntentId": "pi_1234567890",
    "status": "requires_payment_method"
  },
  "timestamp": "2025-08-23T10:30:45"
}
```

### 2. Activate Subscription
**Endpoint:** `POST /api/v1/subscriptions/{subscriptionId}/activate`  
**Authentication:** Required (STUDENT/ADMIN)  
**Description:** Activate subscription after successful payment confirmation.

**Backend Flow:**

1. **Subscription Lookup**: Finds subscription by ID
2. **Status Validation**: Ensures subscription is in PENDING status
3. **Payment Verification**: Verifies payment was successful
4. **Status Update**: Changes status from PENDING to ACTIVE
5. **Points Award**: Awards points to user's wallet based on plan
6. **Date Calculation**: Sets proper start and end dates
7. **Event Publishing**: Publishes subscription activation event via RabbitMQ

```http
POST /api/v1/subscriptions/{subscriptionId}/activate
```

**Path Parameters:**

- `subscriptionId` (UUID): The subscription ID to activate

**Response:**
```json
{
  "success": true,
  "message": "Subscription activated successfully",
  "data": "OK",
  "timestamp": "2025-08-23T10:30:45"
}
```

### 3. Cancel Subscription
**Authentication required**

```http
POST /api/v1/subscriptions/{subscriptionId}/cancel
```

**Path Parameters:**
- `subscriptionId` (UUID): The subscription ID to cancel

**Response:**
```json
{
  "success": true,
  "message": "Subscription cancelled successfully",
  "data": "OK",
  "timestamp": "2025-08-23T10:30:45"
}
```

### 4. Get User Subscriptions
**Authentication required (STUDENT/ADMIN)**

```http
GET /api/v1/subscriptions/user
```

**Response:**
```json
{
  "success": true,
  "message": "Retrieved user subscriptions",
  "data": [
    {
      "id": "650e8400-e29b-41d4-a716-446655440000",
      "userId": "750e8400-e29b-41d4-a716-446655440000",
      "plan": {
        "id": "550e8400-e29b-41d4-a716-446655440000",
        "name": "Premium",
        "billingCycle": "MONTHLY",
        "price": 29.99,
        "currency": "USD",
        "pointsAwarded": 1000,
        "features": ["Feature 1", "Feature 2"],
        "isActive": true,
        "createdAt": "2025-01-15T10:30:45"
      },
      "status": "ACTIVE",
      "startDate": "2025-08-01T10:30:45",
      "endDate": "2025-09-01T10:30:45",
      "autoRenew": true,
      "createdAt": "2025-08-01T10:30:45"
    }
  ],
  "timestamp": "2025-08-23T10:30:45"
}
```

### 5. Get Active Subscription
**Authentication required (STUDENT/ADMIN)**

```http
GET /api/v1/subscriptions/user/active
```

**Response:**
```json
{
  "success": true,
  "message": "Retrieved active subscription",
  "data": {
    // Same structure as individual subscription above, or null if no active subscription
  },
  "timestamp": "2025-08-23T10:30:45"
}
```

### Data Types:
- **SubscriptionStatus**: `"PENDING"` | `"ACTIVE"` | `"CANCELLED"` | `"EXPIRED"`

## Points Management API

### 1. Get User Points Wallet
**Endpoint:** `GET /api/v1/points/wallet`  
**Authentication:** Required (STUDENT/ADMIN)  
**Description:** Retrieve user's points wallet with balance and lifetime statistics.

**Backend Flow:**

1. **User Authentication**: Extracts user ID from JWT token
2. **Wallet Lookup**: Finds existing wallet or creates new one
3. **Balance Calculation**: Calculates current points from transactions
4. **Lifetime Stats**: Aggregates total earned and spent points
5. **Response**: Returns comprehensive wallet information

```http
GET /api/v1/points/wallet
```

**Response:**
```json
{
  "success": true,
  "message": "Retrieved user wallet",
  "data": {
    "id": "850e8400-e29b-41d4-a716-446655440000",
    "userId": "750e8400-e29b-41d4-a716-446655440000",
    "totalPoints": 2500,
    "lifetimeEarned": 5000,
    "lifetimeSpent": 2500
  },
  "timestamp": "2025-08-23T10:30:45"
}
```

### 2. Redeem Points
**Authentication required (STUDENT/ADMIN)**

```http
POST /api/v1/points/redeem
```

**Request Body:**
```json
{
  "points": 100,
  "description": "Course purchase discount",
  "referenceType": "COURSE_PURCHASE", // Optional
  "referenceId": "950e8400-e29b-41d4-a716-446655440000" // Optional
}
```

**Response:**
```json
{
  "success": true,
  "message": "Points redeemed successfully",
  "data": "OK",
  "timestamp": "2025-08-23T10:30:45"
}
```

### 3. Get Transaction History
**Authentication required (STUDENT/ADMIN)**

```http
GET /api/v1/points/transactions?page=0&size=20
```

**Query Parameters:**
- `page` (int): Page number (0-based, default: 0)
- `size` (int): Items per page (default: 20)

**Response:**
```json
{
  "success": true,
  "message": "Retrieved transaction history",
  "data": {
    "content": [
      {
        "id": "a50e8400-e29b-41d4-a716-446655440000",
        "walletId": "850e8400-e29b-41d4-a716-446655440000",
        "points": 1000,
        "transactionType": "EARNED",
        "description": "Subscription purchase bonus",
        "referenceType": "SUBSCRIPTION",
        "referenceId": "650e8400-e29b-41d4-a716-446655440000",
        "createdAt": "2025-08-01T10:30:45"
      }
    ],
    "pageable": {
      "pageNumber": 0,
      "pageSize": 20
    },
    "totalElements": 1,
    "totalPages": 1,
    "first": true,
    "last": true
  },
  "timestamp": "2025-08-23T10:30:45"
}
```

### 4. Award Points (Admin Only)
**Authentication required (ADMIN)**

```http
POST /api/v1/points/award?userId={userId}&points={points}&description={description}&referenceType={referenceType}&referenceId={referenceId}
```

**Query Parameters:**
- `userId` (UUID): User ID to award points to
- `points` (int): Number of points to award
- `description` (String): Description/reason for awarding points
- `referenceType` (String): Optional reference type
- `referenceId` (UUID): Optional reference ID

**Response:**
```json
{
  "success": true,
  "message": "Points awarded successfully",
  "data": "OK",
  "timestamp": "2025-08-23T10:30:45"
}
```

## Webhooks

### Stripe Webhook
**Endpoint:** `POST /api/v1/webhooks/stripe`  
**Authentication:** Webhook signature verification  
**Description:** Handle Stripe payment events for subscription automation.

**Backend Flow:**

1. **Signature Verification**: Validates webhook signature using Stripe secret
2. **Event Parsing**: Parses Stripe event payload
3. **Event Routing**: Routes to appropriate handler based on event type
4. **Business Logic**: Processes payment success/failure events
5. **Subscription Updates**: Updates subscription status based on payment events
6. **Idempotency**: Handles duplicate events gracefully
7. **Response**: Returns success/failure status to Stripe

**Webhook Events Handled:**
- `payment_intent.succeeded` - Activates subscription
- `payment_intent.payment_failed` - Marks subscription as failed
- `invoice.payment_succeeded` - Handles recurring payments
- `customer.subscription.deleted` - Handles subscription cancellations

```http
POST /api/v1/webhooks/stripe
```

**Headers:**

- `Stripe-Signature`: Webhook signature for verification

**Body:** Raw Stripe event payload

This endpoint handles Stripe events like payment confirmations, subscription updates, etc.

## Integration Flow Examples

### 1. Complete Subscription Purchase Flow

```javascript
// Step 1: Get available plans
const plansResponse = await fetch('/api/v1/subscription-plans');
const plans = await plansResponse.json();

// Step 2: Create subscription (triggers payment intent creation)
const subscriptionResponse = await fetch('/api/v1/subscriptions/create', {
  method: 'POST',
  headers: {
    'Authorization': `Bearer ${token}`,
    'Content-Type': 'application/json'
  },
  body: JSON.stringify({
    planId: selectedPlan.id,
    billingCycle: 'MONTHLY',
    email: user.email
  })
});

const subscriptionData = await subscriptionResponse.json();

// Step 3: Use Stripe.js to handle payment on frontend
const stripe = Stripe('pk_test_...');
const { error } = await stripe.confirmCardPayment(
  subscriptionData.data.clientSecret,
  {
    payment_method: {
      card: cardElement,
      billing_details: {
        name: user.name,
        email: user.email
      }
    }
  }
);

// Step 4: Payment success is handled via webhook automatically
// Check subscription status or listen for updates
```

### 2. Check User Subscription Status

```javascript
// Get user's active subscription
const activeSubResponse = await fetch('/api/v1/subscriptions/user/active', {
  headers: {
    'Authorization': `Bearer ${token}`
  }
});

const activeSubscription = await activeSubResponse.json();

if (activeSubscription.data) {
  // User has active subscription
  console.log('Subscription expires:', activeSubscription.data.endDate);
} else {
  // No active subscription - show upgrade options
}
```

### 3. Points Management Flow

```javascript
// Get user's points wallet
const walletResponse = await fetch('/api/v1/points/wallet', {
  headers: {
    'Authorization': `Bearer ${token}`
  }
});

const wallet = await walletResponse.json();
console.log('Available points:', wallet.data.totalPoints);

// Redeem points
const redeemResponse = await fetch('/api/v1/points/redeem', {
  method: 'POST',
  headers: {
    'Authorization': `Bearer ${token}`,
    'Content-Type': 'application/json'
  },
  body: JSON.stringify({
    points: 100,
    description: 'Course discount',
    referenceType: 'COURSE_PURCHASE',
    referenceId: 'course-uuid'
  })
});
```

## Error Handling

### Common HTTP Status Codes:
- **200**: Success
- **400**: Bad Request (validation errors, insufficient points, payment errors)
- **401**: Unauthorized (invalid/missing token)
- **403**: Forbidden (insufficient permissions)
- **404**: Not Found (resource not found)
- **500**: Internal Server Error

### Example Error Response:
```json
{
  "success": false,
  "message": "Payment processing error: Your card was declined",
  "data": null,
  "timestamp": "2025-08-23T10:30:45"
}
```

### Frontend Error Handling Pattern:
```javascript
async function handleApiCall(url, options) {
  try {
    const response = await fetch(url, options);
    const data = await response.json();
    
    if (!data.success) {
      throw new Error(data.message);
    }
    
    return data.data;
  } catch (error) {
    console.error('API Error:', error.message);
    // Show user-friendly error message
    showErrorToUser(error.message);
    throw error;
  }
}
```

## Environment Configuration

### Development:
```javascript
const API_BASE_URL = 'http://localhost:8083';
const STRIPE_PUBLISHABLE_KEY = 'pk_test_51RxrJYKFa3CiRyg4XkkcUhqYGys4gRHuBUuzmkECaRzyG64yCjt6XkGyGjmFGv2JzbGMAaa5pZaKjCNiD2XuzESq00yH9UZwGF';
```

### Production:
```javascript
const API_BASE_URL = 'https://api.edunex.com';
const STRIPE_PUBLISHABLE_KEY = 'pk_live_...'; // Use live key in production
```

## Security Considerations

1. **Never expose Stripe secret keys** on the frontend
2. **Always validate JWT tokens** are present before API calls
3. **Handle token expiration** gracefully with refresh logic
4. **Use HTTPS** in production
5. **Validate user input** before sending to API
6. **Store sensitive data securely** (use secure HTTP-only cookies for tokens when possible)

## Testing

### Test Cards (Development):
- **Success**: `4242424242424242`
- **Decline**: `4000000000000002`
- **Requires 3D Secure**: `4000002500003155`

### Sample Frontend Test:

```javascript
// Test subscription creation
const testSubscription = {
  planId: '550e8400-e29b-41d4-a716-446655440000',
  billingCycle: 'MONTHLY',
  email: 'test@example.com'
};

const response = await fetch('/api/v1/subscriptions/create', {
  method: 'POST',
  headers: {
    'Authorization': `Bearer ${testToken}`,
    'Content-Type': 'application/json'
  },
  body: JSON.stringify(testSubscription)
});

console.log('Subscription created:', await response.json());
```

---

## Backend Architecture & Data Flow Summary

### Overall System Architecture

The subscription service follows a layered architecture pattern:

1. **Controller Layer**: Handles HTTP requests, authentication, and response formatting
2. **Service Layer**: Contains business logic and orchestrates operations  
3. **Repository Layer**: Manages data persistence and database operations
4. **Integration Layer**: Handles external service integrations (Stripe, RabbitMQ)

### Complete Subscription Flow Backend Process

#### 1. Subscription Creation Process

```
Frontend Request → Authentication Middleware → SubscriptionController 
→ SubscriptionService → PaymentService → Stripe API 
→ Database Transaction → Response to Frontend
```

**Detailed Backend Steps:**

1. **Request Reception**: Controller validates request structure and authentication
2. **User Validation**: AuthService extracts and validates user ID from JWT
3. **Plan Validation**: Service verifies subscription plan exists and is active
4. **Stripe Integration**: Creates payment intent via Stripe API
5. **Database Transaction**: 
   - Creates UserSubscription entity with PENDING status
   - Associates with SubscriptionPlan
   - Stores Stripe payment intent ID
6. **Response Generation**: Returns payment intent client secret for frontend

#### 2. Payment Confirmation Flow (Webhook)

```
Stripe Webhook → Signature Verification → Event Processing 
→ Subscription Activation → Points Award → Event Publishing
```

**Detailed Backend Steps:**

1. **Webhook Reception**: WebhookController receives Stripe event
2. **Signature Verification**: Validates request authenticity using webhook secret
3. **Event Processing**: WebhookService routes event based on type
4. **Subscription Update**: Changes status from PENDING to ACTIVE
5. **Points Management**: Awards points to user's wallet based on plan
6. **Date Calculation**: Sets subscription start/end dates based on billing cycle
7. **Event Publishing**: Publishes activation event to RabbitMQ for other services

#### 3. Points System Backend Logic

```
Points Award → Wallet Update → Transaction Record → Balance Calculation
```

**Backend Components:**

1. **Transaction Management**: All point operations create audit trail
2. **Atomic Operations**: Wallet updates use database transactions
3. **Balance Calculation**: Real-time calculation from transaction history
4. **Redemption Validation**: Checks sufficient balance before allowing redemption

### Database Schema Overview

#### Core Entities and Relationships:

- **SubscriptionPlan**: Plan definitions with pricing and features
- **UserSubscription**: User's subscription instances with status tracking
- **UserPointsWallet**: User's points balance and lifetime statistics
- **PointsTransaction**: Audit trail of all points movements
- **Payment**: Payment records linked to Stripe transactions
- **WebhookEvent**: Webhook event processing history

#### Key Business Rules Enforced in Backend:

1. **Single Active Subscription**: Users can have only one ACTIVE subscription at a time
2. **Points Integrity**: All points operations are transactional and auditable
3. **Payment Security**: All payments go through Stripe with webhook confirmation
4. **Status Transitions**: Subscription status follows strict state machine rules
5. **Date Validation**: Subscription dates are calculated based on billing cycles

### Security Implementation

#### Authentication & Authorization:

- **JWT Validation**: All protected endpoints validate Keycloak JWT tokens
- **Role-Based Access**: STUDENT vs ADMIN permissions enforced at method level
- **User Context**: User ID extracted from token for data isolation
- **Webhook Security**: Stripe signature verification for webhook authenticity

#### Data Protection:

- **Input Validation**: All request DTOs have validation annotations
- **SQL Injection Prevention**: JPA/Hibernate handles parameterized queries
- **Sensitive Data**: Stripe keys managed through environment variables
- **Audit Logging**: All critical operations are logged with user context

### Error Handling Strategy

#### Exception Hierarchy:

1. **Validation Errors**: Input validation failures (400 Bad Request)
2. **Authentication Errors**: Invalid/missing tokens (401 Unauthorized)  
3. **Authorization Errors**: Insufficient permissions (403 Forbidden)
4. **Business Logic Errors**: Domain-specific violations (400 Bad Request)
5. **External Service Errors**: Stripe API failures (502 Bad Gateway)
6. **System Errors**: Unexpected exceptions (500 Internal Server Error)

#### Error Response Pattern:

All errors follow consistent ApiResponse format with:
- Success flag (always false for errors)
- Human-readable error message
- Null data field
- Timestamp for debugging

### Performance Considerations

#### Database Optimization:

- **Indexed Queries**: Foreign keys and frequently queried fields are indexed
- **Lazy Loading**: Entity relationships use lazy loading to prevent N+1 queries
- **Connection Pooling**: PostgreSQL connection pool configured for concurrent access
- **Transaction Management**: @Transactional annotations ensure ACID properties

#### Caching Strategy:

- **Plan Caching**: Subscription plans rarely change, suitable for caching
- **User Context**: JWT parsing cached during request lifecycle
- **Stripe Data**: Payment intents cached temporarily to reduce API calls

### Integration Points

#### External Services:

1. **Stripe Payment Processing**:
   - Payment intent creation and confirmation
   - Customer management
   - Webhook event handling
   - Subscription lifecycle management

2. **Keycloak Authentication**:
   - JWT token validation
   - User role and permission extraction
   - Token refresh handling

3. **RabbitMQ Event Publishing**:
   - Subscription lifecycle events
   - Points transaction events
   - Cross-service communication

#### Internal Service Communication:

- **Event-Driven Architecture**: Publishes domain events for other services
- **RESTful APIs**: Exposes standard REST endpoints for frontend consumption
- **Database Isolation**: Each service manages its own database schema

This comprehensive backend overview explains how each endpoint processes requests, maintains data consistency, handles errors, and integrates with external services. The architecture supports scalability, security, and maintainability while providing a robust foundation for the subscription management system.

---

This guide provides all the necessary information for frontend developers to integrate with the Subscription Service API effectively, including detailed backend flows for understanding the complete system behavior.

# Course Service Integration Guide

## Overview
This document explains how the Course Service should integrate with the Subscription Service for the points-based access system.

## System Flow

### 1. Subscription Purchase Flow
```
1. Student subscribes to a plan (Monthly/Yearly)
2. Payment processed via Stripe
3. Stripe webhook triggers subscription activation
4. Points automatically awarded to user's wallet based on subscription plan
5. Points renewed automatically when subscription renews (Stripe handles renewal)
```

### 2. Course Access Flow
```
1. Student attempts to access a course module or quiz
2. Course Service calls Subscription Service to validate points
3. If sufficient points, Course Service calls deduct endpoint
4. Access granted to student
```

## API Endpoints for Course Service

### 1. Validate User Points (Before Access)
**Endpoint:** `POST /api/v1/points/validate`

**Purpose:** Check if user has enough points before granting access

**Parameters:**
- `userId` (UUID) - The student's user ID
- `requiredPoints` (Integer) - Points needed for the resource

**Request:**
```http
POST /api/v1/points/validate?userId=550e8400-e29b-41d4-a716-446655440000&requiredPoints=10
Authorization: Bearer {token}
```

**Response (Success - 200):**
```json
{
  "success": true,
  "message": "User has enough points",
  "data": {
    "hasEnoughPoints": true,
    "currentBalance": 100,
    "requiredPoints": 10,
    "message": "User has enough points"
  }
}
```

**Response (Insufficient - 400):**
```json
{
  "success": false,
  "message": "Insufficient points. You need 5 more points.",
  "data": {
    "hasEnoughPoints": false,
    "currentBalance": 5,
    "requiredPoints": 10,
    "message": "Insufficient points. You need 5 more points."
  }
}
```

### 2. Deduct Points (On Access)
**Endpoint:** `POST /api/v1/points/deduct`

**Purpose:** Deduct points when user successfully accesses a resource

**Parameters:**
- `userId` (UUID) - The student's user ID
- `points` (Integer) - Points to deduct
- `resourceType` (String) - Type of resource (e.g., "COURSE_MODULE", "QUIZ")
- `resourceId` (UUID) - ID of the course module or quiz
- `description` (String) - Human-readable description

**Request:**
```http
POST /api/v1/points/deduct?userId=550e8400-e29b-41d4-a716-446655440000&points=10&resourceType=COURSE_MODULE&resourceId=abc123-def456&description=Access to Introduction to Java
Authorization: Bearer {token}
```

**Response (Success - 200):**
```json
{
  "success": true,
  "message": "Points deducted successfully",
  "data": "OK"
}
```

**Response (Failure - 400):**
```json
{
  "success": false,
  "message": "Insufficient points or wallet not found",
  "data": null
}
```

### 3. Get User Points Balance
**Endpoint:** `GET /api/v1/points/balance`

**Purpose:** Get current points balance for display in UI

**Request:**
```http
GET /api/v1/points/balance
Authorization: Bearer {token}
```

**Response:**
```json
{
  "success": true,
  "message": "Retrieved points balance",
  "data": 100
}
```

## Recommended Implementation Pattern

### In Course Service (Java/Spring Boot Example)

```java
@Service
public class CourseAccessService {
    
    @Autowired
    private RestTemplate restTemplate;
    
    @Value("${subscription.service.url}")
    private String subscriptionServiceUrl;
    
    public boolean grantCourseModuleAccess(UUID userId, UUID moduleId, int requiredPoints) {
        
        // Step 1: Validate points
        String validateUrl = subscriptionServiceUrl + "/api/v1/points/validate" +
            "?userId=" + userId + "&requiredPoints=" + requiredPoints;
        
        ResponseEntity<PointsValidationResponse> validationResponse = 
            restTemplate.postForEntity(validateUrl, null, PointsValidationResponse.class);
        
        if (!validationResponse.getBody().isHasEnoughPoints()) {
            throw new InsufficientPointsException("User does not have enough points");
        }
        
        // Step 2: Deduct points
        String deductUrl = subscriptionServiceUrl + "/api/v1/points/deduct" +
            "?userId=" + userId + 
            "&points=" + requiredPoints +
            "&resourceType=COURSE_MODULE" +
            "&resourceId=" + moduleId +
            "&description=Access to Module: " + getModuleName(moduleId);
        
        ResponseEntity<ApiResponse> deductResponse = 
            restTemplate.postForEntity(deductUrl, null, ApiResponse.class);
        
        if (!deductResponse.getBody().isSuccess()) {
            throw new PointsDeductionException("Failed to deduct points");
        }
        
        // Step 3: Grant access
        return true;
    }
}
```

## Points Award Rules

### Subscription Plans
- **Monthly Plan**: 100 points awarded per month
- **Yearly Plan**: 1200 points awarded per year

### Point Costs (Example - Configure in Course Service)
- Course Module Access: 10 points
- Quiz Attempt: 5 points
- Practice Test: 15 points
- Certificate Generation: 50 points

## Points Renewal

Points are **automatically renewed** when subscription renews via Stripe webhooks:

1. Stripe sends `invoice.payment_succeeded` webhook
2. Subscription Service detects renewal
3. Points automatically added to user's wallet
4. User can continue accessing content

**Note:** You don't need to calculate expiration dates - Stripe handles subscription lifecycle automatically.

## Error Handling

Always handle these scenarios:

1. **Insufficient Points**: Show user their current balance and required points
2. **No Wallet Found**: User may not have subscribed yet
3. **Service Unavailable**: Implement circuit breaker pattern
4. **Race Conditions**: Use the validate-then-deduct pattern to prevent double deduction

## Best Practices

1. **Always validate before deducting** - Two-step process prevents issues
2. **Use idempotency keys** - Prevent duplicate deductions on retry
3. **Log all transactions** - Points transactions are tracked in subscription service
4. **Display balance prominently** - Show users how many points they have
5. **Handle edge cases** - What if user runs out of points mid-course?

## Instructor Earnings

When students spend points on instructor courses, record earnings:

**Endpoint:** `POST /api/v1/instructor/payouts/record-earning`

This is typically called from an admin panel or automated process to track revenue share.

## Security

- All endpoints require Bearer token authentication
- Role-based access control is enforced
- Course Service should use service-to-service authentication
- Never expose internal endpoints to public

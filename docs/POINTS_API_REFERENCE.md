# Points Service API Reference Guide

## Overview
This guide provides comprehensive documentation for the Points Service endpoints, specifically designed for integration with the Course Service. The Points Service manages user points/credits system for accessing premium course content, quizzes, and other educational resources.

## Base URL
```
http://localhost:8080/api/v1/points
```

## Authentication
All endpoints require JWT Bearer token authentication.
```
Authorization: Bearer <jwt_token>
```

---

## 🔍 Endpoint Summary

| Endpoint | Method | Description | Access Level |
|----------|--------|-------------|--------------|
| `/wallet` | GET | Get user's points wallet | STUDENT, ADMIN |
| `/balance` | GET | Get user's current balance | STUDENT, ADMIN |
| `/validate` | POST | Validate if user has enough points | STUDENT, ADMIN, INSTRUCTOR |
| `/deduct` | POST | Deduct points for resource access | STUDENT, ADMIN, INSTRUCTOR |
| `/redeem` | POST | Redeem points for course material | STUDENT, ADMIN |
| `/transactions` | GET | Get transaction history | STUDENT, ADMIN |
| `/award` | POST | Award points to user (Admin only) | INSTRUCTOR, ADMIN |

---

## 📖 Detailed Endpoint Documentation

### 1. Get User Points Wallet
**GET** `/api/v1/points/wallet`

Returns complete wallet information for the authenticated user.

**Headers:**
```http
Authorization: Bearer <jwt_token>
Content-Type: application/json
```

**Response:**
```json
{
  "success": true,
  "message": "Retrieved user wallet",
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "userId": "123e4567-e89b-12d3-a456-426614174000",
    "currentBalance": 150,
    "totalEarned": 200,
    "totalSpent": 50,
    "createdAt": "2025-01-15T10:00:00Z",
    "updatedAt": "2025-01-15T15:30:00Z"
  }
}
```

---

### 2. Get User Points Balance
**GET** `/api/v1/points/balance`

Quick endpoint to get just the current points balance.

**Headers:**
```http
Authorization: Bearer <jwt_token>
```

**Response:**
```json
{
  "success": true,
  "message": "Retrieved points balance",
  "data": 150
}
```

---

### 3. Validate Points (Before Course Access)
**POST** `/api/v1/points/validate`

⚠️ **Important for Course Service Integration**: Always call this endpoint before allowing access to premium content.

**Parameters:**
- `userId` (UUID) - Target user ID
- `requiredPoints` (Integer) - Points needed for the resource

**Example Request:**
```http
POST /api/v1/points/validate?userId=123e4567-e89b-12d3-a456-426614174000&requiredPoints=10
Authorization: Bearer <jwt_token>
```

**Success Response (User has enough points):**
```json
{
  "success": true,
  "message": "User has enough points",
  "data": {
    "hasEnoughPoints": true,
    "currentBalance": 150,
    "requiredPoints": 10,
    "message": "User has sufficient points"
  }
}
```

**Failure Response (Insufficient points):**
```json
{
  "success": false,
  "message": "Insufficient points. Required: 10, Current: 5",
  "data": {
    "hasEnoughPoints": false,
    "currentBalance": 5,
    "requiredPoints": 10,
    "message": "Insufficient points. Required: 10, Current: 5"
  }
}
```

---

### 🎯 4. Deduct Points (Course Service Integration)
**POST** `/api/v1/points/deduct`

**This is the main endpoint for Course Service to deduct points when users access premium content.**

**Parameters:**
- `userId` (UUID) - User ID whose points to deduct
- `points` (Integer) - Number of points to deduct
- `resourceType` (String) - Type of resource (e.g., "COURSE_MODULE", "QUIZ", "ASSIGNMENT")
- `resourceId` (UUID) - ID of the specific resource
- `description` (String) - Human-readable description

**Example Request from Course Service:**
```http
POST /api/v1/points/deduct?userId=123e4567-e89b-12d3-a456-426614174000&points=10&resourceType=COURSE_MODULE&resourceId=789e0123-e45b-67c8-d901-234567890123&description=Access to Advanced Java Module 3
Authorization: Bearer <jwt_token>
```

**Success Response:**
```json
{
  "success": true,
  "message": "Points deducted successfully",
  "data": "OK"
}
```

**Failure Response:**
```json
{
  "success": false,
  "message": "Insufficient points or wallet not found"
}
```

---

## 🛠️ Course Service Integration Examples

### Example 1: Complete Flow for Module Access

```java
// In Course Service - Before granting module access
@Service
public class CourseAccessService {
    
    @Autowired
    private RestTemplate restTemplate;
    
    private static final String POINTS_SERVICE_URL = "http://subscription-service:8080/api/v1/points";
    
    public boolean grantModuleAccess(UUID userId, UUID moduleId, String jwtToken) {
        // Step 1: Get module point cost (from your database)
        int requiredPoints = getModulePointCost(moduleId); // e.g., 10 points
        
        // Step 2: Validate user has enough points
        if (!validateUserPoints(userId, requiredPoints, jwtToken)) {
            throw new InsufficientPointsException("User doesn't have enough points");
        }
        
        // Step 3: Deduct points
        if (!deductPointsForModule(userId, moduleId, requiredPoints, jwtToken)) {
            throw new PointsDeductionException("Failed to deduct points");
        }
        
        // Step 4: Grant access to module
        return grantUserAccessToModule(userId, moduleId);
    }
    
    private boolean validateUserPoints(UUID userId, int requiredPoints, String jwtToken) {
        try {
            String url = POINTS_SERVICE_URL + "/validate?userId=" + userId + "&requiredPoints=" + requiredPoints;
            
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(jwtToken);
            HttpEntity<?> entity = new HttpEntity<>(headers);
            
            ResponseEntity<PointsValidationResponse> response = restTemplate.exchange(
                url, HttpMethod.POST, entity, PointsValidationResponse.class
            );
            
            return response.getStatusCode().is2xxSuccessful() && 
                   response.getBody().getData().isHasEnoughPoints();
                   
        } catch (Exception e) {
            log.error("Error validating points for user: " + userId, e);
            return false;
        }
    }
    
    private boolean deductPointsForModule(UUID userId, UUID moduleId, int points, String jwtToken) {
        try {
            String url = POINTS_SERVICE_URL + "/deduct" +
                "?userId=" + userId +
                "&points=" + points +
                "&resourceType=COURSE_MODULE" +
                "&resourceId=" + moduleId +
                "&description=Access to " + getModuleName(moduleId);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(jwtToken);
            HttpEntity<?> entity = new HttpEntity<>(headers);
            
            ResponseEntity<String> response = restTemplate.exchange(
                url, HttpMethod.POST, entity, String.class
            );
            
            return response.getStatusCode().is2xxSuccessful();
            
        } catch (Exception e) {
            log.error("Error deducting points for user: " + userId, e);
            return false;
        }
    }
}
```

### Example 2: Quiz Access Integration

```java
public class QuizService {
    
    public QuizAttemptResponse startQuizAttempt(UUID userId, UUID quizId, String jwtToken) {
        int quizCost = getQuizPointCost(quizId); // e.g., 5 points
        
        // Validate and deduct points before allowing quiz attempt
        if (!courseAccessService.validateAndDeductPoints(
                userId, quizId, quizCost, "QUIZ", "Quiz: " + getQuizTitle(quizId), jwtToken)) {
            return QuizAttemptResponse.error("Insufficient points for quiz attempt");
        }
        
        // Create quiz attempt record
        return createQuizAttempt(userId, quizId);
    }
}
```

### Example 3: Batch Validation for Course Enrollment

```java
public class EnrollmentService {
    
    public EnrollmentResponse enrollUserInCourse(UUID userId, UUID courseId, String jwtToken) {
        // Get all modules in course
        List<CourseModule> modules = getModulesForCourse(courseId);
        int totalPointsRequired = modules.stream()
            .mapToInt(module -> module.getPointCost())
            .sum();
        
        // Validate user has enough points for entire course
        if (!validateUserPoints(userId, totalPointsRequired, jwtToken)) {
            return EnrollmentResponse.error(
                "Insufficient points. Required: " + totalPointsRequired + 
                " for full course access"
            );
        }
        
        // Don't deduct here - deduct per module as user accesses them
        return createEnrollment(userId, courseId);
    }
}
```

---

## 📋 Resource Types Reference

Use these standardized resource types when calling the deduct endpoint:

| Resource Type | Description | Typical Point Cost |
|---------------|-------------|-------------------|
| `COURSE_MODULE` | Individual course module/lesson | 5-20 points |
| `QUIZ` | Quiz or assessment | 3-10 points |
| `ASSIGNMENT` | Assignments or projects | 10-25 points |
| `LIVE_SESSION` | Live lectures or webinars | 15-30 points |
| `CERTIFICATE` | Course completion certificates | 50-100 points |
| `PREMIUM_CONTENT` | Special premium materials | 20-50 points |

---

## 🔧 Error Handling

### Common Error Responses

**401 Unauthorized:**
```json
{
  "success": false,
  "message": "Unable to extract user ID from token"
}
```

**400 Bad Request (Insufficient Points):**
```json
{
  "success": false,
  "message": "Insufficient points or wallet not found"
}
```

**500 Internal Server Error:**
```json
{
  "success": false,
  "message": "Failed to deduct points: Database connection error"
}
```

### Recommended Error Handling Strategy

```java
try {
    // Call points service
    ResponseEntity<ApiResponse> response = callPointsService();
    
    if (response.getStatusCode().is2xxSuccessful()) {
        // Success - proceed with course access
    } else if (response.getStatusCode() == HttpStatus.BAD_REQUEST) {
        // Insufficient points - show upgrade message
        throw new InsufficientPointsException();
    } else {
        // Other error - retry or show generic error
        throw new ServiceException();
    }
} catch (HttpClientException e) {
    // Network error - implement retry logic
    log.error("Points service unavailable", e);
    throw new ServiceUnavailableException();
}
```

---

## 🚀 Quick Start Checklist for Course Service

1. **Add Points Service URL to configuration:**
   ```properties
   points.service.url=http://subscription-service:8080/api/v1/points
   ```

2. **Create RestTemplate bean if not exists:**
   ```java
   @Bean
   public RestTemplate restTemplate() {
       return new RestTemplate();
   }
   ```

3. **Before any premium content access:**
   - Call `/validate` to check points
   - Call `/deduct` to charge points
   - Grant access only if both succeed

4. **Handle errors gracefully:**
   - Show clear messages for insufficient points
   - Implement retry logic for network errors
   - Log all points-related operations

5. **Test scenarios:**
   - User with sufficient points
   - User with insufficient points
   - Network errors/service unavailable
   - Invalid user IDs

---

## 📞 Support & Contact

For integration issues or questions:
- Check logs in both Course Service and Subscription Service
- Verify JWT token is valid and properly formatted
- Ensure user exists in the system
- Contact the Subscription Service team for wallet-related issues

---

## 🔄 Version History

- **v1.0** - Initial API release
- Current version supports all basic points operations
- Future versions may include bulk operations and advanced reporting

---

**Last Updated:** October 2025  
**API Version:** v1.0  
**Service:** Subscription Service - Points Module
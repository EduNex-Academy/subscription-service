# Subscription & Payment Service

A comprehensive point-based subscription service built with Spring Boot, PostgreSQL, and Stripe integration with JWT authentication and role-based authorization.

## Features

- 🔔 **Subscription Management**: Create, activate, cancel subscriptions
- 💳 **Stripe Integration**: Secure payment processing
- 🎯 **Point System**: Award and redeem points for user engagement
- 📊 **Payment Tracking**: Complete payment history and status tracking
- 🔗 **Webhook Support**: Real-time Stripe event processing
- 🗄️ **PostgreSQL Database**: Robust data persistence
- 🐳 **Docker Support**: Easy deployment with Docker Compose
- 🔐 **JWT Authentication**: Secure API access with role-based authorization
- 📚 **Swagger UI**: Interactive API documentation
- 👥 **Role-Based Access**: Support for STUDENT, INSTRUCTOR, and ADMIN roles

## Tech Stack

- **Backend**: Spring Boot 3.4.7, Java 17
- **Database**: PostgreSQL 15
- **Payment**: Stripe API
- **Authentication**: JWT with OAuth2 Resource Server
- **Documentation**: OpenAPI 3 (Swagger)
- **Containerization**: Docker & Docker Compose
- **ORM**: JPA/Hibernate
- **Build Tool**: Gradle

## Quick Start

### Prerequisites

- Java 17+
- Docker & Docker Compose
- Stripe Account (for payment processing)
- JWT Authentication Server (API Gateway)

### Setup

1. **Clone and navigate to the project:**
   ```bash
   cd subscription-service
   ```

2. **Set up environment variables:**
   ```bash
   copy .env.example .env
   ```
   Edit `.env` file with your credentials:
   ```
   STRIPE_SECRET_KEY=sk_test_your_stripe_secret_key
   STRIPE_WEBHOOK_SECRET=whsec_your_webhook_secret
   JWT_JWK_SET_URI=https://your-auth-server/.well-known/jwks.json
   JWT_ISSUER_URI=https://your-auth-server
   ```

3. **Start the services:**
   ```bash
   docker-compose up -d
   ```

4. **Build the application:**
   ```bash
   ./gradlew build
   ```

5. **Access the application:**
   - API: http://localhost:8083
   - Swagger UI: http://localhost:8083/swagger-ui.html
   - API Docs: http://localhost:8083/v3/api-docs
   - Database: localhost:5434
   - Health Check: http://localhost:8083/actuator/health

## Authentication

The service uses JWT tokens for authentication. All protected endpoints require a valid JWT token in the Authorization header:

```
Authorization: Bearer <your-jwt-token>
```

### User Roles

- **STUDENT**: Can manage their own subscriptions and points
- **INSTRUCTOR**: Can award points to students and manage their own resources
- **ADMIN**: Full access to all endpoints and user management

### JWT Token Claims

Expected JWT token structure:
```json
{
  "sub": "user-uuid",
  "email": "user@example.com",
  "roles": ["STUDENT"],
  "iat": 1234567890,
  "exp": 1234567890
}
```

## API Endpoints

### Public Endpoints
- `GET /api/v1/subscription-plans/**` - Subscription plan information
- `POST /api/v1/webhooks/**` - Webhook endpoints
- `GET /actuator/health` - Health check
- `GET /swagger-ui.html` - API documentation

### Subscription Plans
- `GET /api/v1/subscription-plans` - Get all active plans
- `GET /api/v1/subscription-plans/{planId}` - Get plan by ID
- `GET /api/v1/subscription-plans/by-name/{planName}` - Get plans by name

### Subscriptions (Authenticated)
- `POST /api/v1/subscriptions/create` - Create new subscription
- `POST /api/v1/subscriptions/{id}/activate` - Activate subscription (Admin only)
- `POST /api/v1/subscriptions/{id}/cancel` - Cancel subscription
- `GET /api/v1/subscriptions/user/{userId}` - Get user subscriptions
- `GET /api/v1/subscriptions/user/{userId}/active` - Get active subscription
- `GET /api/v1/subscriptions/my-subscriptions` - Get current user's subscriptions
- `GET /api/v1/subscriptions/my-active` - Get current user's active subscription

### Points System (Authenticated)
- `GET /api/v1/points/wallet/{userId}` - Get user wallet
- `GET /api/v1/points/my-wallet` - Get current user's wallet
- `POST /api/v1/points/redeem` - Redeem points
- `POST /api/v1/points/award` - Award points (Admin/Instructor only)
- `GET /api/v1/points/transactions/{userId}` - Get transaction history
- `GET /api/v1/points/my-transactions` - Get current user's transaction history

### Webhooks
- `POST /api/v1/webhooks/stripe` - Stripe webhook endpoint

## API Documentation

Interactive API documentation is available via Swagger UI at:
- **Local**: http://localhost:8083/swagger-ui.html
- **Production**: https://your-domain/swagger-ui.html

The documentation includes:
- Authentication requirements
- Request/response schemas
- Example payloads
- Error responses
- Try-it-out functionality

## Security Features

### JWT Integration
- Automatic token validation
- User ID extraction from token claims
- Role-based authorization using Spring Security
- CORS configuration for cross-origin requests

### Authorization Rules
- **Students**: Access their own resources only
- **Instructors**: Award points + access their own resources
- **Admins**: Full access to all resources

### API Security
- All sensitive endpoints require authentication
- User ID is extracted from JWT token (not from request)
- Resource-level authorization checks
- Webhook signature verification

## Database Schema

The service includes the following main entities:

- **subscription_plans**: Available subscription plans with Stripe integration
- **user_subscriptions**: User subscription records with status tracking
- **payments**: Payment transactions with Stripe references
- **user_points_wallet**: User point balances and lifetime statistics
- **points_transactions**: Point transaction history with references
- **webhook_events**: Stripe webhook event tracking and processing
- **subscription_history**: Audit trail for subscription changes

## Configuration

### Application Properties

Key configuration options in `application.properties`:

```properties
# Server
server.port=8083

# Database
spring.datasource.url=jdbc:postgresql://localhost:5434/subscription_db
spring.datasource.username=subscription_user
spring.datasource.password=subscription_pass

# JWT Authentication
spring.security.oauth2.resourceserver.jwt.jwk-set-uri=${JWT_JWK_SET_URI}
spring.security.oauth2.resourceserver.jwt.issuer-uri=${JWT_ISSUER_URI}

# Stripe
stripe.api.key=${STRIPE_API_KEY}
stripe.webhook.secret=${STRIPE_WEBHOOK_SECRET}

# Swagger
springdoc.swagger-ui.path=/swagger-ui.html
springdoc.api-docs.path=/v3/api-docs
```

### Default Subscription Plans

The system comes with pre-configured plans:
- **Basic**: $9.99/month (100 points) or $99.99/year (1200 points)
- **Plus**: $19.99/month (250 points) or $199.99/year (3000 points)
- **Pro**: $39.99/month (500 points) or $399.99/year (6000 points)

## Integration with API Gateway

This service is designed to work behind an API Gateway that handles:
- User authentication and JWT token generation
- Rate limiting and request routing
- Load balancing and SSL termination

Expected JWT token format from the API Gateway:
```javascript
/**
 * Extract user ID from JWT token
 */
private String extractUserIdFromToken(Authentication authentication) {
    Jwt jwt = (Jwt) authentication.getPrincipal();
    return jwt.getClaimAsString("sub");
}

/**
 * Extract user roles from JWT token
 */
private List<String> extractUserRoles(Authentication authentication) {
    Jwt jwt = (Jwt) authentication.getPrincipal();
    return jwt.getClaimAsStringList("roles");
}
```

## Development

### Local Development

1. **Start PostgreSQL:**
   ```bash
   docker-compose up subscription-db -d
   ```

2. **Run the application:**
   ```bash
   ./gradlew bootRun
   ```

3. **Access Swagger UI:**
   ```
   http://localhost:8083/swagger-ui.html
   ```

### Testing with Swagger

1. Go to Swagger UI
2. Click "Authorize" button
3. Enter your JWT token: `Bearer <your-token>`
4. Test endpoints interactively

### Building for Production

```bash
./gradlew build
docker build -t subscription-service .
```

## Monitoring

The service includes Spring Boot Actuator for monitoring:
- Health: `/actuator/health`
- Info: `/actuator/info`  
- Metrics: `/actuator/metrics`

Admin access required for detailed actuator endpoints.

## Error Handling

The service includes comprehensive error handling:
- Validation errors with field-level details
- Authentication and authorization errors
- Stripe API errors with user-friendly messages
- Generic exception handling with logging

## Contributing

1. Fork the repository
2. Create a feature branch
3. Add proper Swagger documentation
4. Include authentication tests
5. Submit a pull request

## License

This project is licensed under the MIT License.

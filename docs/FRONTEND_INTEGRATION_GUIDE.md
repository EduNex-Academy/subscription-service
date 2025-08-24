# Frontend Integration Guide for Payment & Subscription Service

This guide provides complete frontend implementation examples for integrating with the Payment & Subscription Service API. All examples use modern JavaScript/TypeScript with proper error handling and best practices.

## Table of Contents
- [Setup & Configuration](#setup--configuration)
- [Payment Management](#payment-management)
- [Points Management](#points-management)
- [Subscription Management](#subscription-management)
- [Subscription Plans](#subscription-plans)
- [Webhook Handling](#webhook-handling)
- [Complete Frontend Examples](#complete-frontend-examples)

## Setup & Configuration

### 1. Environment Variables

```javascript
// config.js
const API_CONFIG = {
  BASE_URL: 'http://localhost:8080/api/v1',
  STRIPE_PUBLIC_KEY: 'pk_test_51RxrJYKFa3CiRyg4XkkcUhqYGys4gRHuBUuzmkECaRzyG64yCjt6XkGyGjmFGv2JzbGMAaa5pZaKjCNiD2XuzESq00yH9UZwGF',
  // Add your actual Stripe public key here
};
```

### 2. API Client Setup

```javascript
// apiClient.js
class ApiClient {
  constructor(baseUrl) {
    this.baseUrl = baseUrl;
    this.token = localStorage.getItem('authToken');
  }

  setAuthToken(token) {
    this.token = token;
    localStorage.setItem('authToken', token);
  }

  async request(endpoint, options = {}) {
    const url = `${this.baseUrl}${endpoint}`;
    
    const defaultHeaders = {
      'Content-Type': 'application/json',
      ...(this.token && { 'Authorization': `Bearer ${this.token}` })
    };

    const config = {
      ...options,
      headers: {
        ...defaultHeaders,
        ...options.headers
      }
    };

    try {
      const response = await fetch(url, config);
      const data = await response.json();
      
      if (!response.ok) {
        throw new Error(data.message || 'API request failed');
      }
      
      return data;
    } catch (error) {
      console.error('API Error:', error);
      throw error;
    }
  }
}

const apiClient = new ApiClient(API_CONFIG.BASE_URL);
```

## Payment Management

### Create Payment Intent

Creates a Stripe payment intent for one-time payments.

**Endpoint:** `POST /api/v1/payments/create-intent`

```javascript
// paymentService.js
class PaymentService {
  /**
   * Create a payment intent for one-time payment
   * @param {Object} paymentData - Payment details
   * @param {string} paymentData.planId - UUID of the subscription plan
   * @param {number} paymentData.amount - Payment amount (e.g., 29.99)
   * @param {string} paymentData.currency - Currency code (e.g., "USD")
   * @param {string} paymentData.email - Customer email
   * @param {string} paymentData.paymentMethodId - Optional: Stripe payment method ID
   */
  async createPaymentIntent(paymentData) {
    try {
      const response = await apiClient.request('/payments/create-intent', {
        method: 'POST',
        body: JSON.stringify(paymentData)
      });
      
      return response.data; // Contains clientSecret, paymentIntentId
    } catch (error) {
      console.error('Failed to create payment intent:', error);
      throw error;
    }
  }

  /**
   * Confirm payment using Stripe
   * @param {string} clientSecret - Client secret from payment intent
   * @param {string} paymentMethodId - Stripe payment method ID
   */
  async confirmPayment(clientSecret, paymentMethodId) {
    const stripe = Stripe(API_CONFIG.STRIPE_PUBLIC_KEY);
    
    try {
      const result = await stripe.confirmPayment({
        clientSecret: clientSecret,
        payment_method: paymentMethodId,
        confirmation_method: 'manual'
      });
      
      if (result.error) {
        throw new Error(result.error.message);
      }
      
      // Confirm with backend
      const confirmResponse = await apiClient.request('/payments/confirm', {
        method: 'POST',
        body: JSON.stringify({
          paymentIntentId: result.paymentIntent.id,
          status: result.paymentIntent.status
        })
      });
      
      return confirmResponse.data;
    } catch (error) {
      console.error('Payment confirmation failed:', error);
      throw error;
    }
  }
}

const paymentService = new PaymentService();
```

### Frontend Payment Component Example (React)

```jsx
// PaymentForm.jsx
import React, { useState } from 'react';
import { loadStripe } from '@stripe/stripe-js';
import {
  Elements,
  CardElement,
  useStripe,
  useElements
} from '@stripe/react-stripe-js';

const stripePromise = loadStripe(API_CONFIG.STRIPE_PUBLIC_KEY);

const PaymentForm = ({ planId, amount, email, onSuccess, onError }) => {
  const stripe = useStripe();
  const elements = useElements();
  const [isProcessing, setIsProcessing] = useState(false);

  const handleSubmit = async (event) => {
    event.preventDefault();
    
    if (!stripe || !elements) {
      return;
    }

    setIsProcessing(true);

    try {
      // Step 1: Create payment intent
      const paymentIntent = await paymentService.createPaymentIntent({
        planId,
        amount,
        currency: 'USD',
        email
      });

      // Step 2: Confirm payment
      const cardElement = elements.getElement(CardElement);
      
      const { error, paymentMethod } = await stripe.createPaymentMethod({
        type: 'card',
        card: cardElement,
        billing_details: {
          email: email,
        },
      });

      if (error) {
        throw new Error(error.message);
      }

      // Step 3: Confirm payment with backend
      const result = await paymentService.confirmPayment(
        paymentIntent.clientSecret,
        paymentMethod.id
      );

      onSuccess(result);
    } catch (error) {
      onError(error.message);
    } finally {
      setIsProcessing(false);
    }
  };

  return (
    <form onSubmit={handleSubmit}>
      <div className="card-element-container">
        <CardElement
          options={{
            style: {
              base: {
                fontSize: '16px',
                color: '#424770',
                '::placeholder': {
                  color: '#aab7c4',
                },
              },
            },
          }}
        />
      </div>
      
      <button 
        type="submit" 
        disabled={!stripe || isProcessing}
        className="payment-button"
      >
        {isProcessing ? 'Processing...' : `Pay $${amount}`}
      </button>
    </form>
  );
};

const PaymentWrapper = (props) => (
  <Elements stripe={stripePromise}>
    <PaymentForm {...props} />
  </Elements>
);

export default PaymentWrapper;
```

## Points Management

### Get User Points Wallet

**Endpoint:** `GET /api/v1/points/wallet`

```javascript
// pointsService.js
class PointsService {
  /**
   * Get user's points wallet information
   */
  async getUserWallet() {
    try {
      const response = await apiClient.request('/points/wallet');
      return response.data; // Contains currentBalance, totalEarned, totalRedeemed
    } catch (error) {
      console.error('Failed to fetch user wallet:', error);
      throw error;
    }
  }

  /**
   * Get user's transaction history
   * @param {number} page - Page number (0-based)
   * @param {number} size - Page size
   */
  async getTransactionHistory(page = 0, size = 10) {
    try {
      const response = await apiClient.request(
        `/points/transactions?page=${page}&size=${size}`
      );
      return response.data; // Paginated list of transactions
    } catch (error) {
      console.error('Failed to fetch transaction history:', error);
      throw error;
    }
  }

  /**
   * Redeem points for rewards
   * @param {Object} redeemData - Redemption details
   * @param {number} redeemData.points - Points to redeem
   * @param {string} redeemData.description - Redemption description
   */
  async redeemPoints(redeemData) {
    try {
      const response = await apiClient.request('/points/redeem', {
        method: 'POST',
        body: JSON.stringify(redeemData)
      });
      return response.data;
    } catch (error) {
      console.error('Failed to redeem points:', error);
      throw error;
    }
  }

  /**
   * Award points to user (Admin only)
   * @param {Object} awardData - Award details
   * @param {string} awardData.userId - User ID to award points to
   * @param {number} awardData.points - Points to award
   * @param {string} awardData.description - Award description
   */
  async awardPoints(awardData) {
    try {
      const response = await apiClient.request('/points/award', {
        method: 'POST',
        body: JSON.stringify(awardData)
      });
      return response.data;
    } catch (error) {
      console.error('Failed to award points:', error);
      throw error;
    }
  }
}

const pointsService = new PointsService();
```

### Points Component Example (React)

```jsx
// PointsWallet.jsx
import React, { useState, useEffect } from 'react';

const PointsWallet = () => {
  const [wallet, setWallet] = useState(null);
  const [transactions, setTransactions] = useState([]);
  const [loading, setLoading] = useState(true);
  const [redeemAmount, setRedeemAmount] = useState('');

  useEffect(() => {
    loadWalletData();
  }, []);

  const loadWalletData = async () => {
    try {
      setLoading(true);
      const [walletData, transactionData] = await Promise.all([
        pointsService.getUserWallet(),
        pointsService.getTransactionHistory(0, 10)
      ]);
      
      setWallet(walletData);
      setTransactions(transactionData.content);
    } catch (error) {
      console.error('Error loading wallet data:', error);
    } finally {
      setLoading(false);
    }
  };

  const handleRedeem = async (e) => {
    e.preventDefault();
    
    try {
      await pointsService.redeemPoints({
        points: parseInt(redeemAmount),
        description: 'Manual redemption'
      });
      
      setRedeemAmount('');
      loadWalletData(); // Reload data
      alert('Points redeemed successfully!');
    } catch (error) {
      alert(`Redemption failed: ${error.message}`);
    }
  };

  if (loading) return <div>Loading...</div>;

  return (
    <div className="points-wallet">
      <div className="wallet-summary">
        <h2>Points Wallet</h2>
        <div className="balance">
          <span className="label">Current Balance:</span>
          <span className="amount">{wallet?.currentBalance || 0} points</span>
        </div>
        <div className="stats">
          <div>Total Earned: {wallet?.totalEarned || 0}</div>
          <div>Total Redeemed: {wallet?.totalRedeemed || 0}</div>
        </div>
      </div>

      <div className="redeem-section">
        <h3>Redeem Points</h3>
        <form onSubmit={handleRedeem}>
          <input
            type="number"
            value={redeemAmount}
            onChange={(e) => setRedeemAmount(e.target.value)}
            placeholder="Points to redeem"
            min="1"
            max={wallet?.currentBalance || 0}
          />
          <button type="submit">Redeem</button>
        </form>
      </div>

      <div className="transaction-history">
        <h3>Transaction History</h3>
        <div className="transactions">
          {transactions.map(transaction => (
            <div key={transaction.id} className="transaction-item">
              <div className="transaction-type">{transaction.type}</div>
              <div className="transaction-amount">
                {transaction.type === 'EARN' ? '+' : '-'}{transaction.points}
              </div>
              <div className="transaction-description">
                {transaction.description}
              </div>
              <div className="transaction-date">
                {new Date(transaction.createdAt).toLocaleDateString()}
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
};

export default PointsWallet;
```

## Subscription Management

### Subscription Service

```javascript
// subscriptionService.js
class SubscriptionService {
  /**
   * Get all user subscriptions
   */
  async getUserSubscriptions() {
    try {
      const response = await apiClient.request('/subscriptions/user');
      return response.data; // Array of user subscriptions
    } catch (error) {
      console.error('Failed to fetch user subscriptions:', error);
      throw error;
    }
  }

  /**
   * Get active subscription
   */
  async getActiveSubscription() {
    try {
      const response = await apiClient.request('/subscriptions/user/active');
      return response.data; // Active subscription object
    } catch (error) {
      console.error('Failed to fetch active subscription:', error);
      throw error;
    }
  }

  /**
   * Create new subscription
   * @param {Object} subscriptionData - Subscription details
   * @param {string} subscriptionData.planId - UUID of subscription plan
   * @param {string} subscriptionData.billingCycle - "MONTHLY" or "YEARLY"
   * @param {string} subscriptionData.email - Customer email
   * @param {string} subscriptionData.paymentMethodId - Optional: Stripe payment method ID
   */
  async createSubscription(subscriptionData) {
    try {
      const response = await apiClient.request('/subscriptions/create', {
        method: 'POST',
        body: JSON.stringify(subscriptionData)
      });
      return response.data; // Contains clientSecret and subscription details
    } catch (error) {
      console.error('Failed to create subscription:', error);
      throw error;
    }
  }

  /**
   * Cancel subscription
   * @param {string} subscriptionId - UUID of subscription to cancel
   */
  async cancelSubscription(subscriptionId) {
    try {
      const response = await apiClient.request(
        `/subscriptions/${subscriptionId}/cancel`,
        { method: 'POST' }
      );
      return response.data;
    } catch (error) {
      console.error('Failed to cancel subscription:', error);
      throw error;
    }
  }

  /**
   * Activate subscription
   * @param {string} subscriptionId - UUID of subscription to activate
   */
  async activateSubscription(subscriptionId) {
    try {
      const response = await apiClient.request(
        `/subscriptions/${subscriptionId}/activate`,
        { method: 'POST' }
      );
      return response.data;
    } catch (error) {
      console.error('Failed to activate subscription:', error);
      throw error;
    }
  }
}

const subscriptionService = new SubscriptionService();
```

### Subscription Management Component (React)

```jsx
// SubscriptionManager.jsx
import React, { useState, useEffect } from 'react';

const SubscriptionManager = () => {
  const [subscriptions, setSubscriptions] = useState([]);
  const [activeSubscription, setActiveSubscription] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    loadSubscriptionData();
  }, []);

  const loadSubscriptionData = async () => {
    try {
      setLoading(true);
      const [userSubs, activeSub] = await Promise.all([
        subscriptionService.getUserSubscriptions(),
        subscriptionService.getActiveSubscription().catch(() => null)
      ]);
      
      setSubscriptions(userSubs);
      setActiveSubscription(activeSub);
    } catch (error) {
      console.error('Error loading subscription data:', error);
    } finally {
      setLoading(false);
    }
  };

  const handleCancel = async (subscriptionId) => {
    if (!window.confirm('Are you sure you want to cancel this subscription?')) {
      return;
    }

    try {
      await subscriptionService.cancelSubscription(subscriptionId);
      loadSubscriptionData(); // Reload data
      alert('Subscription cancelled successfully');
    } catch (error) {
      alert(`Failed to cancel subscription: ${error.message}`);
    }
  };

  const handleActivate = async (subscriptionId) => {
    try {
      await subscriptionService.activateSubscription(subscriptionId);
      loadSubscriptionData(); // Reload data
      alert('Subscription activated successfully');
    } catch (error) {
      alert(`Failed to activate subscription: ${error.message}`);
    }
  };

  if (loading) return <div>Loading subscriptions...</div>;

  return (
    <div className="subscription-manager">
      <h2>My Subscriptions</h2>
      
      {activeSubscription && (
        <div className="active-subscription">
          <h3>Active Subscription</h3>
          <div className="subscription-card active">
            <div className="plan-name">{activeSubscription.planName}</div>
            <div className="billing-cycle">{activeSubscription.billingCycle}</div>
            <div className="status">{activeSubscription.status}</div>
            <div className="next-billing">
              Next billing: {new Date(activeSubscription.nextBillingDate).toLocaleDateString()}
            </div>
            <button 
              onClick={() => handleCancel(activeSubscription.id)}
              className="cancel-button"
            >
              Cancel Subscription
            </button>
          </div>
        </div>
      )}

      <div className="subscription-history">
        <h3>Subscription History</h3>
        <div className="subscriptions-list">
          {subscriptions.map(subscription => (
            <div key={subscription.id} className={`subscription-card ${subscription.status.toLowerCase()}`}>
              <div className="plan-name">{subscription.planName}</div>
              <div className="billing-cycle">{subscription.billingCycle}</div>
              <div className="status">{subscription.status}</div>
              <div className="dates">
                <div>Started: {new Date(subscription.startDate).toLocaleDateString()}</div>
                {subscription.endDate && (
                  <div>Ended: {new Date(subscription.endDate).toLocaleDateString()}</div>
                )}
              </div>
              
              {subscription.status === 'CANCELLED' && (
                <button 
                  onClick={() => handleActivate(subscription.id)}
                  className="activate-button"
                >
                  Reactivate
                </button>
              )}
            </div>
          ))}
        </div>
      </div>
    </div>
  );
};

export default SubscriptionManager;
```

## Subscription Plans

### Subscription Plans Service

```javascript
// subscriptionPlansService.js
class SubscriptionPlansService {
  /**
   * Get all available subscription plans
   */
  async getAllPlans() {
    try {
      const response = await apiClient.request('/subscription-plans');
      return response.data; // Array of subscription plans
    } catch (error) {
      console.error('Failed to fetch subscription plans:', error);
      throw error;
    }
  }

  /**
   * Get subscription plan by ID
   * @param {string} planId - UUID of the plan
   */
  async getPlanById(planId) {
    try {
      const response = await apiClient.request(`/subscription-plans/${planId}`);
      return response.data; // Subscription plan object
    } catch (error) {
      console.error('Failed to fetch subscription plan:', error);
      throw error;
    }
  }

  /**
   * Get subscription plans by name
   * @param {string} planName - Name of the plan
   */
  async getPlansByName(planName) {
    try {
      const response = await apiClient.request(`/subscription-plans/by-name/${planName}`);
      return response.data; // Array of matching plans
    } catch (error) {
      console.error('Failed to fetch subscription plans by name:', error);
      throw error;
    }
  }
}

const subscriptionPlansService = new SubscriptionPlansService();
```

### Subscription Plans Component (React)

```jsx
// SubscriptionPlans.jsx
import React, { useState, useEffect } from 'react';

const SubscriptionPlans = ({ onSelectPlan }) => {
  const [plans, setPlans] = useState([]);
  const [loading, setLoading] = useState(true);
  const [selectedBilling, setSelectedBilling] = useState('MONTHLY');

  useEffect(() => {
    loadPlans();
  }, []);

  const loadPlans = async () => {
    try {
      setLoading(true);
      const plansData = await subscriptionPlansService.getAllPlans();
      setPlans(plansData);
    } catch (error) {
      console.error('Error loading plans:', error);
    } finally {
      setLoading(false);
    }
  };

  const handleSubscribe = async (plan) => {
    try {
      const subscriptionData = {
        planId: plan.id,
        billingCycle: selectedBilling,
        email: 'user@example.com' // Get from user context
      };

      const subscription = await subscriptionService.createSubscription(subscriptionData);
      
      if (onSelectPlan) {
        onSelectPlan(plan, subscription);
      }
    } catch (error) {
      alert(`Failed to create subscription: ${error.message}`);
    }
  };

  if (loading) return <div>Loading plans...</div>;

  return (
    <div className="subscription-plans">
      <h2>Choose Your Plan</h2>
      
      <div className="billing-toggle">
        <label>
          <input
            type="radio"
            value="MONTHLY"
            checked={selectedBilling === 'MONTHLY'}
            onChange={(e) => setSelectedBilling(e.target.value)}
          />
          Monthly
        </label>
        <label>
          <input
            type="radio"
            value="YEARLY"
            checked={selectedBilling === 'YEARLY'}
            onChange={(e) => setSelectedBilling(e.target.value)}
          />
          Yearly (Save 20%)
        </label>
      </div>

      <div className="plans-grid">
        {plans.map(plan => {
          const price = selectedBilling === 'MONTHLY' ? plan.monthlyPrice : plan.yearlyPrice;
          const savings = selectedBilling === 'YEARLY' 
            ? ((plan.monthlyPrice * 12 - plan.yearlyPrice) / (plan.monthlyPrice * 12) * 100).toFixed(0)
            : 0;

          return (
            <div key={plan.id} className="plan-card">
              <div className="plan-header">
                <h3>{plan.name}</h3>
                <div className="price">
                  <span className="amount">${price}</span>
                  <span className="period">/{selectedBilling.toLowerCase()}</span>
                </div>
                {selectedBilling === 'YEARLY' && savings > 0 && (
                  <div className="savings">Save {savings}%</div>
                )}
              </div>
              
              <div className="plan-description">
                {plan.description}
              </div>
              
              <div className="plan-features">
                <ul>
                  {plan.features && plan.features.map((feature, index) => (
                    <li key={index}>{feature}</li>
                  ))}
                </ul>
              </div>
              
              <button 
                className="subscribe-button"
                onClick={() => handleSubscribe(plan)}
              >
                Subscribe to {plan.name}
              </button>
            </div>
          );
        })}
      </div>
    </div>
  );
};

export default SubscriptionPlans;
```

## Complete Frontend Examples

### Complete Subscription Flow

```jsx
// SubscriptionFlow.jsx
import React, { useState } from 'react';
import SubscriptionPlans from './SubscriptionPlans';
import PaymentWrapper from './PaymentForm';

const SubscriptionFlow = () => {
  const [currentStep, setCurrentStep] = useState('plans'); // 'plans', 'payment', 'success'
  const [selectedPlan, setSelectedPlan] = useState(null);
  const [subscriptionData, setSubscriptionData] = useState(null);

  const handlePlanSelect = (plan, subscription) => {
    setSelectedPlan(plan);
    setSubscriptionData(subscription);
    setCurrentStep('payment');
  };

  const handlePaymentSuccess = (paymentResult) => {
    console.log('Payment successful:', paymentResult);
    setCurrentStep('success');
  };

  const handlePaymentError = (error) => {
    console.error('Payment failed:', error);
    alert(`Payment failed: ${error}`);
  };

  return (
    <div className="subscription-flow">
      {currentStep === 'plans' && (
        <SubscriptionPlans onSelectPlan={handlePlanSelect} />
      )}
      
      {currentStep === 'payment' && selectedPlan && (
        <div className="payment-step">
          <h2>Complete Your Subscription</h2>
          <div className="plan-summary">
            <h3>{selectedPlan.name}</h3>
            <p>Amount: ${subscriptionData.amount}</p>
          </div>
          
          <PaymentWrapper
            planId={selectedPlan.id}
            amount={subscriptionData.amount}
            email="user@example.com" // Get from user context
            onSuccess={handlePaymentSuccess}
            onError={handlePaymentError}
          />
        </div>
      )}
      
      {currentStep === 'success' && (
        <div className="success-step">
          <h2>🎉 Subscription Successful!</h2>
          <p>Thank you for subscribing to {selectedPlan?.name}</p>
          <button onClick={() => window.location.href = '/dashboard'}>
            Go to Dashboard
          </button>
        </div>
      )}
    </div>
  );
};

export default SubscriptionFlow;
```

### Error Handling Utility

```javascript
// errorHandler.js
export class ApiError extends Error {
  constructor(message, status, code) {
    super(message);
    this.status = status;
    this.code = code;
    this.name = 'ApiError';
  }
}

export const handleApiError = (error) => {
  if (error.status === 401) {
    // Redirect to login
    window.location.href = '/login';
    return 'Authentication required';
  }
  
  if (error.status === 403) {
    return 'You don\'t have permission to perform this action';
  }
  
  if (error.status === 400) {
    return error.message || 'Invalid request data';
  }
  
  if (error.status >= 500) {
    return 'Server error. Please try again later.';
  }
  
  return error.message || 'An unexpected error occurred';
};
```

## Best Practices

### 1. Environment-based Configuration

```javascript
// environments.js
const environments = {
  development: {
    API_BASE_URL: 'http://localhost:8080/api/v1',
    STRIPE_PUBLIC_KEY: 'pk_test_...'
  },
  production: {
    API_BASE_URL: 'https://api.yourapp.com/api/v1',
    STRIPE_PUBLIC_KEY: 'pk_live_...'
  }
};

export const config = environments[process.env.NODE_ENV || 'development'];
```

### 2. Loading States and Error Boundaries

```jsx
// LoadingWrapper.jsx
import React from 'react';

const LoadingWrapper = ({ loading, error, children, onRetry }) => {
  if (loading) {
    return (
      <div className="loading-container">
        <div className="spinner"></div>
        <p>Loading...</p>
      </div>
    );
  }

  if (error) {
    return (
      <div className="error-container">
        <h3>Something went wrong</h3>
        <p>{error}</p>
        {onRetry && (
          <button onClick={onRetry} className="retry-button">
            Try Again
          </button>
        )}
      </div>
    );
  }

  return children;
};

export default LoadingWrapper;
```

### 3. Security Considerations

- Always validate user inputs on both frontend and backend
- Store JWT tokens securely (consider using httpOnly cookies)
- Implement proper CORS configuration
- Use HTTPS in production
- Never expose sensitive keys in frontend code
- Implement rate limiting for API calls

### 4. Testing

```javascript
// apiClient.test.js
import { ApiClient } from './apiClient';

describe('ApiClient', () => {
  let apiClient;

  beforeEach(() => {
    apiClient = new ApiClient('http://localhost:8080/api/v1');
  });

  test('should make authenticated requests', async () => {
    // Mock fetch
    global.fetch = jest.fn(() =>
      Promise.resolve({
        ok: true,
        json: () => Promise.resolve({ data: 'test' }),
      })
    );

    apiClient.setAuthToken('test-token');
    const result = await apiClient.request('/test');

    expect(fetch).toHaveBeenCalledWith(
      'http://localhost:8080/api/v1/test',
      expect.objectContaining({
        headers: expect.objectContaining({
          'Authorization': 'Bearer test-token'
        })
      })
    );
  });
});
```

This guide provides a complete foundation for integrating with your payment and subscription service. Adapt the examples to match your specific frontend framework and requirements.

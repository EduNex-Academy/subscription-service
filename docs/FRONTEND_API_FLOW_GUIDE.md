# Frontend API Calling Flow Guide

This document provides a complete guide for implementing frontend integration with the Subscription Service API. It covers all API endpoints, calling sequences, and implementation examples.

## Table of Contents
1. [API Base Configuration](#api-base-configuration)
2. [Authentication Setup](#authentication-setup)
3. [Subscription Plans Flow](#subscription-plans-flow)
4. [Subscription Creation Flow](#subscription-creation-flow)
5. [Points Management Flow](#points-management-flow)
6. [User Subscription Management](#user-subscription-management)
7. [Error Handling](#error-handling)
8. [Complete Implementation Examples](#complete-implementation-examples)

## API Base Configuration

### Environment Setup
```javascript
// config.js
const API_CONFIG = {
  BASE_URL: 'http://localhost:8080/api/v1',
  STRIPE_PUBLIC_KEY: 'pk_test_your_stripe_public_key_here',
  TIMEOUT: 30000
};

// API Endpoints
const ENDPOINTS = {
  // Subscription Plans (No Auth Required)
  PLANS: '/subscription-plans',
  PLAN_BY_ID: (id) => `/subscription-plans/${id}`,
  PLAN_BY_NAME: (name) => `/subscription-plans/by-name/${name}`,
  
  // Subscriptions (Auth Required)
  SUBSCRIPTION_SETUP: '/subscriptions/setup',
  SUBSCRIPTION_COMPLETE: '/subscriptions/complete',
  SUBSCRIPTION_CANCEL: (id) => `/subscriptions/${id}/cancel`,
  SUBSCRIPTION_CONFIRM: '/subscriptions/confirm',
  SUBSCRIPTION_CLEANUP: '/subscriptions/cleanup',
  USER_SUBSCRIPTIONS: '/subscriptions/user',
  ACTIVE_SUBSCRIPTION: '/subscriptions/user/active',
  
  // Points (Auth Required)
  POINTS_WALLET: '/points/wallet',
  POINTS_REDEEM: '/points/redeem',
  POINTS_TRANSACTIONS: '/points/transactions',
  
  // Debug
  DEBUG_PLAN: (id) => `/subscriptions/debug/${id}`
};
```

### API Client Setup
```javascript
// apiClient.js
class ApiClient {
  constructor() {
    this.baseUrl = API_CONFIG.BASE_URL;
    this.timeout = API_CONFIG.TIMEOUT;
  }

  setAuthToken(token) {
    this.token = token;
    localStorage.setItem('authToken', token);
  }

  getAuthToken() {
    return this.token || localStorage.getItem('authToken');
  }

  async request(endpoint, options = {}) {
    const url = `${this.baseUrl}${endpoint}`;
    
    const headers = {
      'Content-Type': 'application/json',
      ...options.headers
    };

    const token = this.getAuthToken();
    if (token) {
      headers['Authorization'] = `Bearer ${token}`;
    }

    const config = {
      method: 'GET',
      headers,
      ...options
    };

    try {
      const controller = new AbortController();
      const timeoutId = setTimeout(() => controller.abort(), this.timeout);

      const response = await fetch(url, {
        ...config,
        signal: controller.signal
      });

      clearTimeout(timeoutId);

      if (!response.ok) {
        const errorData = await response.json().catch(() => ({}));
        throw new Error(errorData.message || `HTTP ${response.status}: ${response.statusText}`);
      }

      return await response.json();
    } catch (error) {
      console.error(`API request failed: ${endpoint}`, error);
      throw error;
    }
  }
}

const apiClient = new ApiClient();
```

## Authentication Setup

```javascript
// authService.js
class AuthService {
  setToken(token) {
    apiClient.setAuthToken(token);
  }

  logout() {
    localStorage.removeItem('authToken');
    apiClient.setAuthToken(null);
  }

  isAuthenticated() {
    return !!apiClient.getAuthToken();
  }
}

const authService = new AuthService();
```

## Subscription Plans Flow

### 1. Fetch All Available Plans (No Auth Required)
```javascript
// subscriptionPlanService.js
class SubscriptionPlanService {
  
  /**
   * Get all available subscription plans
   * @returns {Promise<Array>} Array of subscription plans
   */
  async getAllPlans() {
    try {
      const response = await apiClient.request(ENDPOINTS.PLANS);
      return response.data; // Array of SubscriptionPlanDto
    } catch (error) {
      console.error('Failed to fetch subscription plans:', error);
      throw error;
    }
  }

  /**
   * Get specific plan by ID
   * @param {string} planId - UUID of the plan
   * @returns {Promise<Object>} Plan details
   */
  async getPlanById(planId) {
    try {
      const response = await apiClient.request(ENDPOINTS.PLAN_BY_ID(planId));
      return response.data; // SubscriptionPlanDto
    } catch (error) {
      console.error(`Failed to fetch plan ${planId}:`, error);
      throw error;
    }
  }

  /**
   * Get plans by name (e.g., "Basic", "Premium")
   * @param {string} planName - Name of the plan
   * @returns {Promise<Array>} Matching plans
   */
  async getPlansByName(planName) {
    try {
      const response = await apiClient.request(ENDPOINTS.PLAN_BY_NAME(planName));
      return response.data; // Array of SubscriptionPlanDto
    } catch (error) {
      console.error(`Failed to fetch plans for ${planName}:`, error);
      throw error;
    }
  }
}

const subscriptionPlanService = new SubscriptionPlanService();
```

### React Component Example - Plan Selection
```jsx
// PlanSelector.jsx
import React, { useState, useEffect } from 'react';

const PlanSelector = ({ onPlanSelect }) => {
  const [plans, setPlans] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    const fetchPlans = async () => {
      try {
        setLoading(true);
        const planData = await subscriptionPlanService.getAllPlans();
        setPlans(planData);
      } catch (err) {
        setError(err.message);
      } finally {
        setLoading(false);
      }
    };

    fetchPlans();
  }, []);

  if (loading) return <div>Loading plans...</div>;
  if (error) return <div>Error: {error}</div>;

  return (
    <div className="plan-selector">
      <h2>Choose Your Plan</h2>
      {plans.map(plan => (
        <div key={plan.planId} className="plan-card">
          <h3>{plan.planName}</h3>
          <p>{plan.description}</p>
          <p>Price: ${plan.price} / {plan.billingInterval}</p>
          <p>Points: {plan.pointsValue}</p>
          <button onClick={() => onPlanSelect(plan)}>
            Select Plan
          </button>
        </div>
      ))}
    </div>
  );
};
```

## Subscription Creation Flow

### 2-Step Subscription Process

The subscription creation follows a 2-step process:
1. **Setup**: Create setup intent and collect payment method
2. **Complete**: Create actual subscription after payment method is confirmed

```javascript
// subscriptionService.js
class SubscriptionService {

  /**
   * Step 1: Setup subscription with payment method collection
   * @param {Object} subscriptionData - Subscription setup data
   * @returns {Promise<Object>} Setup intent and client secret
   */
  async setupSubscription(subscriptionData) {
    try {
      const response = await apiClient.request(ENDPOINTS.SUBSCRIPTION_SETUP, {
        method: 'POST',
        body: JSON.stringify(subscriptionData)
      });
      return response.data;
      /*
      Response format:
      {
        setupIntentId: "seti_xxx",
        clientSecret: "seti_xxx_secret_xxx",
        customerId: "cus_xxx",
        planId: "uuid"
      }
      */
    } catch (error) {
      console.error('Subscription setup failed:', error);
      throw error;
    }
  }

  /**
   * Step 2: Complete subscription after payment method setup
   * @param {string} customerId - Stripe customer ID
   * @param {string} planId - Plan UUID
   * @returns {Promise<Object>} Created subscription
   */
  async completeSubscription(customerId, planId) {
    try {
      const response = await apiClient.request(
        `${ENDPOINTS.SUBSCRIPTION_COMPLETE}?customerId=${customerId}&planId=${planId}`,
        { method: 'POST' }
      );
      return response.data; // UserSubscriptionDto
    } catch (error) {
      console.error('Subscription completion failed:', error);
      throw error;
    }
  }

  /**
   * Cancel subscription
   * @param {string} subscriptionId - Subscription UUID
   * @returns {Promise<string>} Cancellation result
   */
  async cancelSubscription(subscriptionId) {
    try {
      const response = await apiClient.request(ENDPOINTS.SUBSCRIPTION_CANCEL(subscriptionId), {
        method: 'POST'
      });
      return response.data;
    } catch (error) {
      console.error('Subscription cancellation failed:', error);
      throw error;
    }
  }

  /**
   * Manual confirmation for stuck subscriptions
   * @param {string} stripeSubscriptionId - Stripe subscription ID
   * @returns {Promise<Object>} Confirmation result
   */
  async confirmSubscriptionPayment(stripeSubscriptionId) {
    try {
      const response = await apiClient.request(
        `${ENDPOINTS.SUBSCRIPTION_CONFIRM}?stripeSubscriptionId=${stripeSubscriptionId}`,
        { method: 'POST' }
      );
      return response.data;
    } catch (error) {
      console.error('Subscription confirmation failed:', error);
      throw error;
    }
  }

  /**
   * Cleanup incomplete subscriptions
   * @returns {Promise<Array>} Cleanup results
   */
  async cleanupIncompleteSubscriptions() {
    try {
      const response = await apiClient.request(ENDPOINTS.SUBSCRIPTION_CLEANUP, {
        method: 'POST'
      });
      return response.data;
    } catch (error) {
      console.error('Subscription cleanup failed:', error);
      throw error;
    }
  }

  /**
   * Get user's subscriptions
   * @returns {Promise<Array>} User subscriptions
   */
  async getUserSubscriptions() {
    try {
      const response = await apiClient.request(ENDPOINTS.USER_SUBSCRIPTIONS);
      return response.data; // Array of UserSubscriptionDto
    } catch (error) {
      console.error('Failed to fetch user subscriptions:', error);
      throw error;
    }
  }

  /**
   * Get user's active subscription
   * @returns {Promise<Object|null>} Active subscription or null
   */
  async getActiveSubscription() {
    try {
      const response = await apiClient.request(ENDPOINTS.ACTIVE_SUBSCRIPTION);
      return response.data; // UserSubscriptionDto or null
    } catch (error) {
      console.error('Failed to fetch active subscription:', error);
      throw error;
    }
  }
}

const subscriptionService = new SubscriptionService();
```

### Stripe Integration for Frontend
```javascript
// stripeService.js
import { loadStripe } from '@stripe/stripe-js';

class StripeService {
  constructor() {
    this.stripePromise = loadStripe(API_CONFIG.STRIPE_PUBLIC_KEY);
  }

  async getStripe() {
    return await this.stripePromise;
  }

  /**
   * Confirm setup intent with payment method
   * @param {string} clientSecret - Setup intent client secret
   * @param {Object} paymentMethod - Payment method details
   * @returns {Promise<Object>} Stripe confirmation result
   */
  async confirmSetupIntent(clientSecret, paymentMethod) {
    const stripe = await this.getStripe();
    
    try {
      const result = await stripe.confirmSetupIntent(clientSecret, {
        payment_method: paymentMethod
      });

      if (result.error) {
        throw new Error(result.error.message);
      }

      return result;
    } catch (error) {
      console.error('Setup intent confirmation failed:', error);
      throw error;
    }
  }
}

const stripeService = new StripeService();
```

### Complete Subscription Flow Component
```jsx
// SubscriptionFlow.jsx
import React, { useState } from 'react';
import { Elements, CardElement, useStripe, useElements } from '@stripe/react-stripe-js';

const SubscriptionForm = ({ selectedPlan, onSuccess, onError }) => {
  const stripe = useStripe();
  const elements = useElements();
  const [isProcessing, setIsProcessing] = useState(false);
  const [currentStep, setCurrentStep] = useState('setup'); // setup, confirm, complete

  const handleSubscriptionFlow = async () => {
    if (!stripe || !elements || !selectedPlan) return;

    setIsProcessing(true);

    try {
      // Step 1: Setup subscription
      setCurrentStep('setup');
      const setupData = {
        planId: selectedPlan.planId,
        email: 'user@example.com' // Get from user context
      };

      const setupResult = await subscriptionService.setupSubscription(setupData);

      // Step 2: Confirm setup intent with Stripe
      setCurrentStep('confirm');
      const cardElement = elements.getElement(CardElement);
      
      const confirmResult = await stripeService.confirmSetupIntent(
        setupResult.clientSecret,
        {
          card: cardElement,
          billing_details: {
            email: setupData.email
          }
        }
      );

      // Step 3: Complete subscription creation
      setCurrentStep('complete');
      const subscription = await subscriptionService.completeSubscription(
        setupResult.customerId,
        selectedPlan.planId
      );

      onSuccess(subscription);

    } catch (error) {
      console.error('Subscription flow failed:', error);
      onError(error);
    } finally {
      setIsProcessing(false);
    }
  };

  return (
    <div className="subscription-form">
      <h3>Subscribe to {selectedPlan?.planName}</h3>
      
      <div className="payment-details">
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
        onClick={handleSubscriptionFlow}
        disabled={!stripe || isProcessing}
        className="subscribe-button"
      >
        {isProcessing ? `Processing (${currentStep})...` : `Subscribe for $${selectedPlan?.price}`}
      </button>
    </div>
  );
};

// Wrapper with Stripe Elements
const SubscriptionFlow = ({ selectedPlan, onSuccess, onError }) => {
  return (
    <Elements stripe={stripeService.stripePromise}>
      <SubscriptionForm 
        selectedPlan={selectedPlan}
        onSuccess={onSuccess}
        onError={onError}
      />
    </Elements>
  );
};
```

## Points Management Flow

```javascript
// pointsService.js
class PointsService {

  /**
   * Get user's points wallet
   * @returns {Promise<Object>} Wallet information
   */
  async getUserWallet() {
    try {
      const response = await apiClient.request(ENDPOINTS.POINTS_WALLET);
      return response.data; // UserPointsWalletDto
    } catch (error) {
      console.error('Failed to fetch user wallet:', error);
      throw error;
    }
  }

  /**
   * Redeem points for course material
   * @param {Object} redeemData - Redemption details
   * @returns {Promise<string>} Redemption result
   */
  async redeemPoints(redeemData) {
    try {
      const response = await apiClient.request(ENDPOINTS.POINTS_REDEEM, {
        method: 'POST',
        body: JSON.stringify(redeemData)
      });
      return response.data;
    } catch (error) {
      console.error('Points redemption failed:', error);
      throw error;
    }
  }

  /**
   * Get user's transaction history
   * @param {number} page - Page number (0-based)
   * @param {number} size - Items per page
   * @returns {Promise<Object>} Paginated transactions
   */
  async getTransactionHistory(page = 0, size = 20) {
    try {
      const response = await apiClient.request(
        `${ENDPOINTS.POINTS_TRANSACTIONS}?page=${page}&size=${size}`
      );
      return response.data; // Page<PointsTransactionDto>
    } catch (error) {
      console.error('Failed to fetch transaction history:', error);
      throw error;
    }
  }
}

const pointsService = new PointsService();
```

### Points Management Component
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
      setTransactions(transactionData.content || []);
    } catch (error) {
      console.error('Failed to load wallet data:', error);
    } finally {
      setLoading(false);
    }
  };

  const handleRedeem = async () => {
    if (!redeemAmount || isNaN(redeemAmount)) return;

    try {
      await pointsService.redeemPoints({
        pointsToRedeem: parseInt(redeemAmount),
        description: 'Course material redemption'
      });
      
      // Reload wallet data
      await loadWalletData();
      setRedeemAmount('');
      alert('Points redeemed successfully!');
    } catch (error) {
      alert(`Redemption failed: ${error.message}`);
    }
  };

  if (loading) return <div>Loading wallet...</div>;

  return (
    <div className="points-wallet">
      <div className="wallet-balance">
        <h2>Your Points Balance</h2>
        <div className="balance-amount">{wallet?.currentBalance || 0} Points</div>
        <p>Total Earned: {wallet?.totalEarned || 0}</p>
        <p>Total Redeemed: {wallet?.totalRedeemed || 0}</p>
      </div>

      <div className="redeem-section">
        <h3>Redeem Points</h3>
        <input
          type="number"
          value={redeemAmount}
          onChange={(e) => setRedeemAmount(e.target.value)}
          placeholder="Enter points to redeem"
          min="1"
          max={wallet?.currentBalance || 0}
        />
        <button onClick={handleRedeem} disabled={!redeemAmount}>
          Redeem Points
        </button>
      </div>

      <div className="transaction-history">
        <h3>Recent Transactions</h3>
        {transactions.map(transaction => (
          <div key={transaction.transactionId} className="transaction-item">
            <span className="transaction-type">{transaction.transactionType}</span>
            <span className="transaction-amount">{transaction.pointsAmount}</span>
            <span className="transaction-date">{new Date(transaction.transactionDate).toLocaleDateString()}</span>
            <span className="transaction-description">{transaction.description}</span>
          </div>
        ))}
      </div>
    </div>
  );
};
```

## User Subscription Management

```jsx
// SubscriptionDashboard.jsx
import React, { useState, useEffect } from 'react';

const SubscriptionDashboard = () => {
  const [activeSubscription, setActiveSubscription] = useState(null);
  const [allSubscriptions, setAllSubscriptions] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    loadSubscriptionData();
  }, []);

  const loadSubscriptionData = async () => {
    try {
      setLoading(true);
      const [active, all] = await Promise.all([
        subscriptionService.getActiveSubscription(),
        subscriptionService.getUserSubscriptions()
      ]);
      
      setActiveSubscription(active);
      setAllSubscriptions(all);
    } catch (error) {
      console.error('Failed to load subscription data:', error);
    } finally {
      setLoading(false);
    }
  };

  const handleCancelSubscription = async (subscriptionId) => {
    if (!confirm('Are you sure you want to cancel this subscription?')) return;

    try {
      await subscriptionService.cancelSubscription(subscriptionId);
      await loadSubscriptionData(); // Refresh data
      alert('Subscription cancelled successfully');
    } catch (error) {
      alert(`Cancellation failed: ${error.message}`);
    }
  };

  const handleCleanupIncomplete = async () => {
    try {
      const results = await subscriptionService.cleanupIncompleteSubscriptions();
      await loadSubscriptionData(); // Refresh data
      alert(`Cleanup completed: ${results.length} subscriptions processed`);
    } catch (error) {
      alert(`Cleanup failed: ${error.message}`);
    }
  };

  if (loading) return <div>Loading subscriptions...</div>;

  return (
    <div className="subscription-dashboard">
      <div className="active-subscription">
        <h2>Active Subscription</h2>
        {activeSubscription ? (
          <div className="subscription-card active">
            <h3>{activeSubscription.planName}</h3>
            <p>Status: {activeSubscription.status}</p>
            <p>Started: {new Date(activeSubscription.startDate).toLocaleDateString()}</p>
            <p>Next Billing: {new Date(activeSubscription.endDate).toLocaleDateString()}</p>
            <button 
              onClick={() => handleCancelSubscription(activeSubscription.subscriptionId)}
              className="cancel-button"
            >
              Cancel Subscription
            </button>
          </div>
        ) : (
          <p>No active subscription found</p>
        )}
      </div>

      <div className="subscription-history">
        <h2>Subscription History</h2>
        {allSubscriptions.map(subscription => (
          <div key={subscription.subscriptionId} className={`subscription-card ${subscription.status.toLowerCase()}`}>
            <h3>{subscription.planName}</h3>
            <p>Status: {subscription.status}</p>
            <p>Period: {new Date(subscription.startDate).toLocaleDateString()} - {new Date(subscription.endDate).toLocaleDateString()}</p>
            {subscription.stripeSubscriptionId && (
              <p>Stripe ID: {subscription.stripeSubscriptionId}</p>
            )}
          </div>
        ))}
      </div>

      <div className="admin-actions">
        <h3>Troubleshooting</h3>
        <button onClick={handleCleanupIncomplete} className="cleanup-button">
          Fix Incomplete Subscriptions
        </button>
        <p className="help-text">
          Use this if you have payment methods attached but subscriptions aren't activating
        </p>
      </div>
    </div>
  );
};
```

## Error Handling

### Global Error Handler
```javascript
// errorHandler.js
class ErrorHandler {
  static handle(error, context = '') {
    console.error(`Error in ${context}:`, error);

    // Parse API error responses
    if (error.message) {
      return this.getErrorMessage(error.message);
    }

    return 'An unexpected error occurred. Please try again.';
  }

  static getErrorMessage(message) {
    // Common error mappings
    const errorMappings = {
      'Unable to extract user ID from token': 'Please log in again',
      'Setup request already in progress': 'Please wait for current request to complete',
      'Payment processing error': 'Payment failed. Please check your payment details',
      'Insufficient points': 'You don\'t have enough points for this action',
      'Failed to create subscription': 'Subscription creation failed. Please try again'
    };

    // Check for mapped errors
    for (const [key, value] of Object.entries(errorMappings)) {
      if (message.includes(key)) {
        return value;
      }
    }

    return message;
  }

  static isAuthError(error) {
    return error.message && (
      error.message.includes('Unable to extract user ID') ||
      error.message.includes('401') ||
      error.message.includes('Unauthorized')
    );
  }
}
```

## Complete Implementation Examples

### Main App Integration
```jsx
// App.jsx
import React, { useState, useEffect } from 'react';
import PlanSelector from './components/PlanSelector';
import SubscriptionFlow from './components/SubscriptionFlow';
import SubscriptionDashboard from './components/SubscriptionDashboard';
import PointsWallet from './components/PointsWallet';

const App = () => {
  const [user, setUser] = useState(null);
  const [selectedPlan, setSelectedPlan] = useState(null);
  const [currentView, setCurrentView] = useState('plans');

  useEffect(() => {
    // Initialize auth token if available
    const token = localStorage.getItem('authToken');
    if (token) {
      authService.setToken(token);
      setUser({ authenticated: true });
    }
  }, []);

  const handlePlanSelect = (plan) => {
    setSelectedPlan(plan);
    setCurrentView('subscribe');
  };

  const handleSubscriptionSuccess = (subscription) => {
    alert('Subscription created successfully!');
    setCurrentView('dashboard');
  };

  const handleSubscriptionError = (error) => {
    const message = ErrorHandler.handle(error, 'Subscription Flow');
    alert(message);
    
    if (ErrorHandler.isAuthError(error)) {
      authService.logout();
      setUser(null);
    }
  };

  if (!user) {
    return (
      <div className="login-prompt">
        <h2>Please log in to manage subscriptions</h2>
        <button onClick={() => {
          // Simulate login
          const token = 'your-jwt-token-here';
          authService.setToken(token);
          setUser({ authenticated: true });
        }}>
          Login
        </button>
      </div>
    );
  }

  return (
    <div className="app">
      <nav className="nav-bar">
        <button onClick={() => setCurrentView('plans')}>Plans</button>
        <button onClick={() => setCurrentView('dashboard')}>My Subscriptions</button>
        <button onClick={() => setCurrentView('points')}>Points Wallet</button>
        <button onClick={() => {
          authService.logout();
          setUser(null);
        }}>Logout</button>
      </nav>

      <main className="main-content">
        {currentView === 'plans' && (
          <PlanSelector onPlanSelect={handlePlanSelect} />
        )}
        
        {currentView === 'subscribe' && selectedPlan && (
          <SubscriptionFlow
            selectedPlan={selectedPlan}
            onSuccess={handleSubscriptionSuccess}
            onError={handleSubscriptionError}
          />
        )}
        
        {currentView === 'dashboard' && (
          <SubscriptionDashboard />
        )}
        
        {currentView === 'points' && (
          <PointsWallet />
        )}
      </main>
    </div>
  );
};

export default App;
```

## API Call Sequence Summary

### 1. Initial App Load
```
1. GET /subscription-plans → Get available plans (no auth)
2. If authenticated: GET /subscriptions/user/active → Get active subscription
```

### 2. Subscription Creation
```
1. POST /subscriptions/setup → Create setup intent
2. Stripe.confirmSetupIntent() → Confirm payment method
3. POST /subscriptions/complete → Create subscription
4. GET /subscriptions/user → Verify creation
```

### 3. Points Management
```
1. GET /points/wallet → Get current balance
2. GET /points/transactions → Get transaction history
3. POST /points/redeem → Redeem points
```

### 4. Subscription Management
```
1. GET /subscriptions/user → Get all subscriptions
2. POST /subscriptions/{id}/cancel → Cancel subscription
3. POST /subscriptions/cleanup → Fix incomplete subscriptions
```

## Testing the Implementation

### Quick Test Checklist
1. ✅ Load subscription plans without authentication
2. ✅ Authenticate user and load personal data
3. ✅ Create subscription with valid payment method
4. ✅ Verify points are awarded after subscription
5. ✅ Test subscription cancellation
6. ✅ Test points redemption
7. ✅ Test cleanup for stuck subscriptions

This guide provides everything needed to implement a complete frontend integration with the Subscription Service API. The examples use modern JavaScript/React but can be adapted to any frontend framework.

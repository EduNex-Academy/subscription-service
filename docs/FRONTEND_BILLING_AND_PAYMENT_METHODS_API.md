# Frontend Billing and Payment Methods API Guide

This guide covers the new APIs for Billing History and Payment Methods that fetch real data directly from Stripe, bypassing potential webhook issues.

## Overview

Two new API endpoints have been added to handle frontend billing and payment management:

1. **Billing History API** - Fetches actual payment history from Stripe
2. **Payment Methods API** - Manages customer payment methods

## API Endpoints

### 1. Billing History

#### GET `/api/v1/payments/billing-history`

Retrieves the user's billing history directly from Stripe.

**Query Parameters:**
- `limit` (optional, default: 50) - Number of records to fetch

**Response:**
```json
{
  "success": true,
  "message": "Billing history retrieved successfully",
  "data": {
    "history": [
      {
        "id": "in_1234567890",
        "description": "Subscription Payment",
        "date": "2024-01-15 10:30:00",
        "amount": 29.99,
        "currency": "USD",
        "status": "paid",
        "invoiceUrl": "https://invoice.stripe.com/...",
        "paymentMethod": "Card",
        "subscriptionName": "Subscription"
      }
    ],
    "totalCount": 3,
    "hasMore": false
  }
}
```

**Frontend Usage:**
```javascript
const fetchBillingHistory = async () => {
  try {
    const response = await fetch('/api/v1/payments/billing-history?limit=50', {
      headers: {
        'Authorization': `Bearer ${token}`,
        'Content-Type': 'application/json'
      }
    });
    const data = await response.json();
    
    if (data.success) {
      setBillingHistory(data.data.history);
    }
  } catch (error) {
    console.error('Failed to fetch billing history:', error);
  }
};
```

### 2. Payment Methods

#### GET `/api/v1/payments/payment-methods`

Retrieves the user's saved payment methods from Stripe.

**Response:**
```json
{
  "success": true,
  "message": "Payment methods retrieved successfully",
  "data": {
    "paymentMethods": [
      {
        "id": "pm_1234567890",
        "type": "card",
        "brand": "visa",
        "last4": "4242",
        "expMonth": 12,
        "expYear": 2025,
        "isDefault": true,
        "billingName": "John Doe",
        "country": "US"
      }
    ],
    "defaultPaymentMethodId": "pm_1234567890"
  }
}
```

**Frontend Usage:**
```javascript
const fetchPaymentMethods = async () => {
  try {
    const response = await fetch('/api/v1/payments/payment-methods', {
      headers: {
        'Authorization': `Bearer ${token}`,
        'Content-Type': 'application/json'
      }
    });
    const data = await response.json();
    
    if (data.success) {
      setPaymentMethods(data.data.paymentMethods);
      setDefaultPaymentMethod(data.data.defaultPaymentMethodId);
    }
  } catch (error) {
    console.error('Failed to fetch payment methods:', error);
  }
};
```

### 3. Add Payment Method (Setup Intent)

#### POST `/api/v1/payments/setup-intent`

Creates a Stripe setup intent for adding new payment methods.

**Response:**
```json
{
  "success": true,
  "message": "Setup intent created successfully",
  "data": {
    "clientSecret": "seti_1234567890_secret_xyz",
    "setupIntentId": "seti_1234567890",
    "customerId": "cus_1234567890"
  }
}
```

#### POST `/api/v1/payments/payment-methods`

Attaches a payment method to the customer after successful setup.

**Request Body:**
```json
{
  "paymentMethodId": "pm_1234567890",
  "setAsDefault": true
}
```

**Complete Frontend Flow for Adding Payment Method:**
```javascript
import { loadStripe } from '@stripe/stripe-js';

const addPaymentMethod = async () => {
  try {
    // 1. Create setup intent
    const setupResponse = await fetch('/api/v1/payments/setup-intent', {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${token}`,
        'Content-Type': 'application/json'
      }
    });
    const setupData = await setupResponse.json();
    
    if (!setupData.success) {
      throw new Error(setupData.message);
    }
    
    // 2. Use Stripe.js to collect payment method
    const stripe = await loadStripe('your_stripe_publishable_key');
    const { error, setupIntent } = await stripe.confirmCardSetup(
      setupData.data.clientSecret,
      {
        payment_method: {
          card: cardElement, // Your Stripe card element
          billing_details: {
            name: 'Customer Name',
          },
        }
      }
    );
    
    if (error) {
      throw new Error(error.message);
    }
    
    // 3. Attach payment method to customer
    const attachResponse = await fetch('/api/v1/payments/payment-methods', {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${token}`,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        paymentMethodId: setupIntent.payment_method,
        setAsDefault: true
      })
    });
    
    const attachData = await attachResponse.json();
    if (attachData.success) {
      // Refresh payment methods list
      fetchPaymentMethods();
    }
    
  } catch (error) {
    console.error('Failed to add payment method:', error);
  }
};
```

### 4. Manage Payment Methods

#### DELETE `/api/v1/payments/payment-methods/{paymentMethodId}`

Removes a payment method.

```javascript
const removePaymentMethod = async (paymentMethodId) => {
  try {
    const response = await fetch(`/api/v1/payments/payment-methods/${paymentMethodId}`, {
      method: 'DELETE',
      headers: {
        'Authorization': `Bearer ${token}`,
        'Content-Type': 'application/json'
      }
    });
    const data = await response.json();
    
    if (data.success) {
      // Refresh payment methods list
      fetchPaymentMethods();
    }
  } catch (error) {
    console.error('Failed to remove payment method:', error);
  }
};
```

#### PUT `/api/v1/payments/payment-methods/{paymentMethodId}/default`

Sets a payment method as default.

```javascript
const setDefaultPaymentMethod = async (paymentMethodId) => {
  try {
    const response = await fetch(`/api/v1/payments/payment-methods/${paymentMethodId}/default`, {
      method: 'PUT',
      headers: {
        'Authorization': `Bearer ${token}`,
        'Content-Type': 'application/json'
      }
    });
    const data = await response.json();
    
    if (data.success) {
      // Refresh payment methods list
      fetchPaymentMethods();
    }
  } catch (error) {
    console.error('Failed to set default payment method:', error);
  }
};
```

## React Component Examples

### Billing History Component

```jsx
import React, { useState, useEffect } from 'react';

const BillingHistory = () => {
  const [billingHistory, setBillingHistory] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    fetchBillingHistory();
  }, []);

  const fetchBillingHistory = async () => {
    try {
      setLoading(true);
      const response = await fetch('/api/v1/payments/billing-history', {
        headers: {
          'Authorization': `Bearer ${localStorage.getItem('token')}`,
          'Content-Type': 'application/json'
        }
      });
      const data = await response.json();
      
      if (data.success) {
        setBillingHistory(data.data.history);
      }
    } catch (error) {
      console.error('Failed to fetch billing history:', error);
    } finally {
      setLoading(false);
    }
  };

  if (loading) return <div>Loading...</div>;

  return (
    <div className="billing-history">
      <h2>Billing History</h2>
      <p>View your past payments and invoices</p>
      
      {billingHistory.length === 0 ? (
        <p>No billing history found.</p>
      ) : (
        <div className="history-list">
          {billingHistory.map((item) => (
            <div key={item.id} className="history-item">
              <div className="date">{new Date(item.date).toLocaleDateString()}</div>
              <div className="amount">${item.amount}</div>
              <div className="status">{item.status}</div>
              {item.invoiceUrl && (
                <a href={item.invoiceUrl} target="_blank" rel="noopener noreferrer">
                  View Invoice
                </a>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  );
};

export default BillingHistory;
```

### Payment Methods Component

```jsx
import React, { useState, useEffect } from 'react';
import { loadStripe } from '@stripe/stripe-js';
import { Elements, CardElement, useStripe, useElements } from '@stripe/react-stripe-js';

const stripePromise = loadStripe('your_stripe_publishable_key');

const PaymentMethods = () => {
  const [paymentMethods, setPaymentMethods] = useState([]);
  const [defaultPaymentMethodId, setDefaultPaymentMethodId] = useState(null);
  const [showAddForm, setShowAddForm] = useState(false);

  useEffect(() => {
    fetchPaymentMethods();
  }, []);

  const fetchPaymentMethods = async () => {
    try {
      const response = await fetch('/api/v1/payments/payment-methods', {
        headers: {
          'Authorization': `Bearer ${localStorage.getItem('token')}`,
          'Content-Type': 'application/json'
        }
      });
      const data = await response.json();
      
      if (data.success) {
        setPaymentMethods(data.data.paymentMethods);
        setDefaultPaymentMethodId(data.data.defaultPaymentMethodId);
      }
    } catch (error) {
      console.error('Failed to fetch payment methods:', error);
    }
  };

  const removePaymentMethod = async (paymentMethodId) => {
    try {
      const response = await fetch(`/api/v1/payments/payment-methods/${paymentMethodId}`, {
        method: 'DELETE',
        headers: {
          'Authorization': `Bearer ${localStorage.getItem('token')}`,
          'Content-Type': 'application/json'
        }
      });
      const data = await response.json();
      
      if (data.success) {
        fetchPaymentMethods();
      }
    } catch (error) {
      console.error('Failed to remove payment method:', error);
    }
  };

  const setDefaultPaymentMethod = async (paymentMethodId) => {
    try {
      const response = await fetch(`/api/v1/payments/payment-methods/${paymentMethodId}/default`, {
        method: 'PUT',
        headers: {
          'Authorization': `Bearer ${localStorage.getItem('token')}`,
          'Content-Type': 'application/json'
        }
      });
      const data = await response.json();
      
      if (data.success) {
        fetchPaymentMethods();
      }
    } catch (error) {
      console.error('Failed to set default payment method:', error);
    }
  };

  return (
    <div className="payment-methods">
      <h2>Payment Methods</h2>
      <p>Manage your payment methods</p>
      
      <button onClick={() => setShowAddForm(true)}>
        Add Payment Method
      </button>
      
      {showAddForm && (
        <Elements stripe={stripePromise}>
          <AddPaymentMethodForm 
            onSuccess={() => {
              setShowAddForm(false);
              fetchPaymentMethods();
            }}
            onCancel={() => setShowAddForm(false)}
          />
        </Elements>
      )}
      
      <div className="payment-methods-list">
        {paymentMethods.map((pm) => (
          <div key={pm.id} className="payment-method-item">
            <div className="card-info">
              <span className="brand">{pm.brand.charAt(0).toUpperCase() + pm.brand.slice(1)}</span>
              <span>ending in {pm.last4}</span>
            </div>
            <div className="expiry">Expires {pm.expMonth}/{pm.expYear}</div>
            {pm.isDefault && <span className="default-badge">Default</span>}
            <div className="actions">
              {!pm.isDefault && (
                <button onClick={() => setDefaultPaymentMethod(pm.id)}>
                  Set as Default
                </button>
              )}
              <button onClick={() => removePaymentMethod(pm.id)}>
                Remove
              </button>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
};

const AddPaymentMethodForm = ({ onSuccess, onCancel }) => {
  const stripe = useStripe();
  const elements = useElements();
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (event) => {
    event.preventDefault();
    
    if (!stripe || !elements) return;
    
    setLoading(true);
    
    try {
      // Create setup intent
      const setupResponse = await fetch('/api/v1/payments/setup-intent', {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${localStorage.getItem('token')}`,
          'Content-Type': 'application/json'
        }
      });
      const setupData = await setupResponse.json();
      
      if (!setupData.success) {
        throw new Error(setupData.message);
      }
      
      // Confirm setup intent with card
      const { error, setupIntent } = await stripe.confirmCardSetup(
        setupData.data.clientSecret,
        {
          payment_method: {
            card: elements.getElement(CardElement),
            billing_details: {
              name: 'Customer Name',
            },
          }
        }
      );
      
      if (error) {
        throw new Error(error.message);
      }
      
      // Attach payment method
      const attachResponse = await fetch('/api/v1/payments/payment-methods', {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${localStorage.getItem('token')}`,
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({
          paymentMethodId: setupIntent.payment_method,
          setAsDefault: true
        })
      });
      
      const attachData = await attachResponse.json();
      if (attachData.success) {
        onSuccess();
      }
      
    } catch (error) {
      console.error('Failed to add payment method:', error);
    } finally {
      setLoading(false);
    }
  };

  return (
    <form onSubmit={handleSubmit} className="add-payment-form">
      <CardElement />
      <div className="form-actions">
        <button type="button" onClick={onCancel}>Cancel</button>
        <button type="submit" disabled={!stripe || loading}>
          {loading ? 'Adding...' : 'Add Payment Method'}
        </button>
      </div>
    </form>
  );
};

export default PaymentMethods;
```

## Important Notes

1. **Real Stripe Data**: These APIs fetch actual data from Stripe, bypassing any webhook issues you've experienced.

2. **Authentication**: All endpoints require Bearer token authentication.

3. **Error Handling**: The APIs return consistent error responses. Always check the `success` field in the response.

4. **Stripe Integration**: For adding payment methods, you'll need to integrate with Stripe.js on the frontend.

5. **Security**: Payment method creation uses Stripe's Setup Intents, which is the recommended secure approach.

6. **Status Reliability**: Since data comes directly from Stripe, you'll get accurate payment statuses instead of "PENDING" from potentially failed webhooks.

## Frontend Dependencies

Make sure to install the required Stripe libraries:

```bash
npm install @stripe/stripe-js @stripe/react-stripe-js
```

## Testing

You can test these endpoints using tools like Postman or directly from your frontend application. Make sure to include the Authorization header with a valid Bearer token.

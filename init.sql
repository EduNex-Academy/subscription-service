-- Subscription Plans
CREATE TABLE subscription_plans (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(50) NOT NULL,            -- Basic, Plus, Pro
    billing_cycle VARCHAR(20) NOT NULL,   -- Monthly, Yearly
    price DECIMAL(10,2) NOT NULL,
    currency VARCHAR(3) DEFAULT 'USD',
    points_awarded INTEGER NOT NULL,      -- Points given when subscribing
    stripe_price_id VARCHAR(255),         -- Stripe price ID
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Plan Features (for JPA @ElementCollection)
CREATE TABLE plan_features (
    plan_id UUID NOT NULL REFERENCES subscription_plans(id) ON DELETE CASCADE,
    feature VARCHAR(255) NOT NULL,
    PRIMARY KEY (plan_id, feature)
);

-- User Subscriptions
CREATE TABLE user_subscriptions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,                -- External user reference
    plan_id UUID NOT NULL REFERENCES subscription_plans(id),
    status VARCHAR(20) DEFAULT 'ACTIVE',  -- ACTIVE, CANCELLED, EXPIRED, PENDING
    stripe_subscription_id VARCHAR(255),  -- Stripe subscription ID
    stripe_customer_id VARCHAR(255),      -- Stripe customer ID
    start_date TIMESTAMP NOT NULL,
    end_date TIMESTAMP NOT NULL,
    auto_renew BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Payments
CREATE TABLE payments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    subscription_id UUID REFERENCES user_subscriptions(id),
    amount DECIMAL(10,2) NOT NULL,
    currency VARCHAR(3) DEFAULT 'USD',
    status VARCHAR(20) DEFAULT 'PENDING', -- PENDING, COMPLETED, FAILED, REFUNDED
    payment_method VARCHAR(50),           -- STRIPE, PAYPAL, etc.
    stripe_payment_intent_id VARCHAR(255), -- Stripe payment intent ID
    stripe_invoice_id VARCHAR(255),       -- Stripe invoice ID
    failure_reason TEXT,                  -- Reason for payment failure
    processed_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- User Points Wallet
CREATE TABLE user_points_wallet (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID UNIQUE NOT NULL,         -- One wallet per user
    total_points INTEGER NOT NULL DEFAULT 0,
    lifetime_earned INTEGER NOT NULL DEFAULT 0,
    lifetime_spent INTEGER NOT NULL DEFAULT 0,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Points Transactions
CREATE TABLE points_transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    wallet_id UUID NOT NULL REFERENCES user_points_wallet(id) ON DELETE CASCADE,
    user_id UUID NOT NULL,
    transaction_type VARCHAR(20) NOT NULL, -- EARN, REDEEM, EXPIRED
    points INTEGER NOT NULL,
    description TEXT,                      -- e.g. "Subscribed to Pro Plan", "Watched Video"
    reference_type VARCHAR(50),           -- SUBSCRIPTION, VIDEO_WATCH, COURSE_COMPLETION
    reference_id UUID,                    -- ID of the related entity
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Subscription History (for analytics and tracking)
CREATE TABLE subscription_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    subscription_id UUID NOT NULL REFERENCES user_subscriptions(id),
    action VARCHAR(50) NOT NULL,         -- CREATED, RENEWED, CANCELLED, EXPIRED
    previous_status VARCHAR(20),
    new_status VARCHAR(20),
    reason TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Webhook Events (for Stripe webhook handling)
CREATE TABLE webhook_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    stripe_event_id VARCHAR(255) UNIQUE NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    processed BOOLEAN DEFAULT FALSE,
    processing_attempts INTEGER DEFAULT 0,
    last_processing_error TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP
);

-- Indexes for better performance
CREATE INDEX idx_user_subscriptions_user_id ON user_subscriptions(user_id);
CREATE INDEX idx_user_subscriptions_status ON user_subscriptions(status);
CREATE INDEX idx_payments_user_id ON payments(user_id);
CREATE INDEX idx_payments_status ON payments(status);
CREATE INDEX idx_points_transactions_wallet_id ON points_transactions(wallet_id);
CREATE INDEX idx_points_transactions_user_id ON points_transactions(user_id);
CREATE INDEX idx_subscription_history_user_id ON subscription_history(user_id);
CREATE INDEX idx_webhook_events_stripe_event_id ON webhook_events(stripe_event_id);

-- Insert default subscription plans
INSERT INTO subscription_plans (id, name, billing_cycle, price, points_awarded, stripe_price_id) VALUES
('550e8400-e29b-41d4-a716-446655440001', 'Basic', 'MONTHLY', 9.99,  100, 'price_1RzKdTKFa3CiRyg4CJQnMisH'),
('550e8400-e29b-41d4-a716-446655440002', 'Basic', 'YEARLY', 99.99, 1200,'price_1RzKdxKFa3CiRyg4nsnhygau'),
('550e8400-e29b-41d4-a716-446655440003', 'Plus', 'MONTHLY', 19.99, 250,'price_1RzKebKFa3CiRyg4E8UjPaM0'),
('550e8400-e29b-41d4-a716-446655440004', 'Plus', 'YEARLY', 199.99, 3000,'price_1RzKenKFa3CiRyg4ITmcOKpr'),
('550e8400-e29b-41d4-a716-446655440005', 'Pro', 'MONTHLY', 39.99, 500,'price_1RzKfBKFa3CiRyg4sPXz8gPA'),
('550e8400-e29b-41d4-a716-446655440006', 'Pro', 'YEARLY', 399.99, 6000,'price_1RzKfKKFa3CiRyg4a8IPRvQJ');

-- Insert plan features
INSERT INTO plan_features (plan_id, feature) VALUES
-- Basic Monthly
('550e8400-e29b-41d4-a716-446655440001', 'Access to basic courses'),
('550e8400-e29b-41d4-a716-446655440001', '5 downloads per month'),
('550e8400-e29b-41d4-a716-446655440001', 'Email support'),
-- Basic Yearly
('550e8400-e29b-41d4-a716-446655440002', 'Access to basic courses'),
('550e8400-e29b-41d4-a716-446655440002', '5 downloads per month'),
('550e8400-e29b-41d4-a716-446655440002', 'Email support'),
-- Plus Monthly
('550e8400-e29b-41d4-a716-446655440003', 'Access to all courses'),
('550e8400-e29b-41d4-a716-446655440003', '20 downloads per month'),
('550e8400-e29b-41d4-a716-446655440003', 'Priority support'),
('550e8400-e29b-41d4-a716-446655440003', 'Offline viewing'),
-- Plus Yearly
('550e8400-e29b-41d4-a716-446655440004', 'Access to all courses'),
('550e8400-e29b-41d4-a716-446655440004', '20 downloads per month'),
('550e8400-e29b-41d4-a716-446655440004', 'Priority support'),
('550e8400-e29b-41d4-a716-446655440004', 'Offline viewing'),
-- Pro Monthly
('550e8400-e29b-41d4-a716-446655440005', 'Access to all courses'),
('550e8400-e29b-41d4-a716-446655440005', 'Unlimited downloads'),
('550e8400-e29b-41d4-a716-446655440005', '24/7 support'),
('550e8400-e29b-41d4-a716-446655440005', 'Offline viewing'),
('550e8400-e29b-41d4-a716-446655440005', 'Certificate of completion'),
-- Pro Yearly
('550e8400-e29b-41d4-a716-446655440006', 'Access to all courses'),
('550e8400-e29b-41d4-a716-446655440006', 'Unlimited downloads'),
('550e8400-e29b-41d4-a716-446655440006', '24/7 support'),
('550e8400-e29b-41d4-a716-446655440006', 'Offline viewing'),
('550e8400-e29b-41d4-a716-446655440006', 'Certificate of completion');

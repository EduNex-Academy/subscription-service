-- Migration script for instructor payout system and points enhancements
-- Execute this after existing tables

-- Create Instructor Payouts Table FIRST (since earnings references it)
CREATE TABLE IF NOT EXISTS instructor_payouts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    instructor_id UUID NOT NULL,
    amount NUMERIC(10, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    period_start TIMESTAMP NOT NULL,
    period_end TIMESTAMP NOT NULL,
    stripe_payout_id VARCHAR(255),
    stripe_transfer_id VARCHAR(255),
    description TEXT,
    failure_reason TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    paid_at TIMESTAMP
);

-- Create Instructor Earnings Table (references payouts)
CREATE TABLE IF NOT EXISTS instructor_earnings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    instructor_id UUID NOT NULL,
    course_id UUID,
    amount NUMERIC(10, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    earning_type VARCHAR(30) NOT NULL,
    subscription_id UUID,
    description TEXT,
    payout_id UUID,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_earning_payout FOREIGN KEY (payout_id) REFERENCES instructor_payouts(id)
);

-- Create indexes for better query performance
CREATE INDEX IF NOT EXISTS idx_instructor_earnings_instructor ON instructor_earnings(instructor_id);
CREATE INDEX IF NOT EXISTS idx_instructor_earnings_payout ON instructor_earnings(payout_id);
CREATE INDEX IF NOT EXISTS idx_instructor_earnings_created ON instructor_earnings(created_at);
CREATE INDEX IF NOT EXISTS idx_instructor_payouts_instructor ON instructor_payouts(instructor_id);
CREATE INDEX IF NOT EXISTS idx_instructor_payouts_status ON instructor_payouts(status);
CREATE INDEX IF NOT EXISTS idx_instructor_payouts_stripe ON instructor_payouts(stripe_payout_id);

-- Update existing tables if needed
ALTER TABLE user_points_wallet ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE points_transactions ADD COLUMN IF NOT EXISTS created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;

-- Create trigger function for updated_at
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Create triggers
CREATE TRIGGER update_instructor_earnings_updated_at BEFORE UPDATE ON instructor_earnings
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_instructor_payouts_updated_at BEFORE UPDATE ON instructor_payouts
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Add comments for documentation
COMMENT ON TABLE instructor_earnings IS 'Stores individual earning records for instructors from various sources';
COMMENT ON TABLE instructor_payouts IS 'Tracks payout batches sent to instructors';
COMMENT ON COLUMN instructor_earnings.earning_type IS 'Types: COURSE_ENROLLMENT, SUBSCRIPTION_REVENUE_SHARE, BONUS, ADJUSTMENT';
COMMENT ON COLUMN instructor_payouts.status IS 'Status: PENDING, PROCESSING, PAID, FAILED, CANCELLED';

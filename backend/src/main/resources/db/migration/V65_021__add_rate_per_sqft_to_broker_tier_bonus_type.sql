-- Migration V65_021: Add RATE_PER_SQFT to broker_tier_bonus_type enum
-- Kept in its own migration per Postgres requirement that freshly-added enum values
-- cannot be used in the same transaction that created them.

ALTER TYPE broker_tier_bonus_type ADD VALUE IF NOT EXISTS 'RATE_PER_SQFT';

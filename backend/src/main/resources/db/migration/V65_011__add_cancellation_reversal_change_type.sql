-- 06-BROKER-NETWORK-ENGINE.md §9, build-order step 9 -- distinguishes a
-- cancellation-driven designation change from an ordinary completion-
-- driven promotion (AUTOMATIC) and a future manual one (MANUAL, step 8).
-- Its own ALTER TYPE, never combined with a migration that uses the new
-- value in the same transaction -- Postgres forbids that (same rule
-- V65_003 already followed for DESIGNATION on broker_commission_type).
ALTER TYPE designation_change_type ADD VALUE 'CANCELLATION_REVERSAL';

-- Migration V65_022: Backfill existing broker tiers with RATE_PER_SQFT and matching designation rates

UPDATE broker_tier bt
SET bonus_type = 'RATE_PER_SQFT',
    bonus_value = ds.rate_per_sqft
FROM designation_slab ds
WHERE bt.name = ds.name
  AND (ds.org_id = bt.org_id OR ds.org_id IS NULL)
  AND ds.deleted_at IS NULL
  AND bt.deleted_at IS NULL;

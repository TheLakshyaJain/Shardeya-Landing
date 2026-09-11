-- Migration V65_020: Replace legacy Bronze/Silver/Gold/Platinum tiers with real designation tiers

-- 1. Detach old tier references
UPDATE broker_partner bp
SET tier_id = NULL
WHERE tier_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM broker_tier bt 
    WHERE bt.id = bp.tier_id 
      AND bt.name IN ('Business Executive', 'Senior Business Executive', 'Business Development Officer', 'Business Manager', 'Assistant Sales Director', 'Sales Director', 'Vice President', 'President')
  );

-- 2. Clear old tier history
DELETE FROM broker_tier_history;

-- 3. Delete any legacy tiers (Bronze, Silver, Gold, Platinum)
DELETE FROM broker_tier
WHERE name IN ('Bronze', 'Silver', 'Gold', 'Platinum');

-- 4. Seed the 8 real tiers for all BUILDER orgs if not already present
INSERT INTO broker_tier (id, org_id, name, name_hi, min_deals, max_deals, bonus_type, bonus_value, sort_order)
SELECT
    gen_random_uuid(),
    o.id,
    t.name,
    t.name_hi,
    t.min_deals,
    t.max_deals,
    'NONE'::broker_tier_bonus_type,
    0,
    t.sort_order
FROM organization o
CROSS JOIN (VALUES
    ('Business Executive',           'बिज़नेस एक्ज़िक्यूटिव',              0,  0,    1),
    ('Senior Business Executive',    'सीनियर बिज़नेस एक्ज़िक्यूटिव',        1,  1,    2),
    ('Business Development Officer', 'बिज़नेस डेवलपमेंट ऑफिसर',            2,  2,    3),
    ('Business Manager',             'बिज़नेस मैनेजर',                     3,  5,    4),
    ('Assistant Sales Director',     'असिस्टेंट सेल्स डायरेक्टर',           6,  9,    5),
    ('Sales Director',               'सेल्स डायरेक्टर',                    10, 14,   6),
    ('Vice President',                'वाइस प्रेसिडेंट',                    15, 19,   7),
    ('President',                     'प्रेसिडेंट',                         20, NULL::INTEGER, 8)
) AS t(name, name_hi, min_deals, max_deals, sort_order)
WHERE o.type = 'BUILDER' AND o.deletion_requested_at IS NULL
  AND NOT EXISTS (
    SELECT 1 FROM broker_tier bt 
    WHERE bt.org_id = o.id AND bt.name = t.name AND bt.deleted_at IS NULL
  );

-- 5. Link existing brokers to their matching tier based on designation or deal count
UPDATE broker_partner bp
SET tier_id = bt.id
FROM designation_slab ds
JOIN broker_tier bt ON bt.name = ds.name
WHERE bp.current_designation_id = ds.id
  AND bt.org_id = bp.org_id
  AND bt.deleted_at IS NULL
  AND bp.tier_id IS NULL;

UPDATE broker_partner bp
SET tier_id = bt.id
FROM broker_tier bt
WHERE bp.tier_id IS NULL
  AND bt.org_id = bp.org_id
  AND bt.min_deals = 0
  AND bt.deleted_at IS NULL;

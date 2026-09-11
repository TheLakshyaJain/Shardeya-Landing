-- 06-BROKER-NETWORK-ENGINE.md §5 -- the 8 system-default slabs, org_id
-- NULL. Idempotent (ON CONFLICT DO NOTHING) per this project's own seed-
-- migration convention.
INSERT INTO designation_slab (id, org_id, name, name_hi, min_team_sales, max_team_sales, rate_per_sqft, sort_order)
VALUES
    (gen_random_uuid(), NULL, 'Business Executive',           'बिज़नेस एक्ज़िक्यूटिव',              0,  0,    160, 1),
    (gen_random_uuid(), NULL, 'Senior Business Executive',    'सीनियर बिज़नेस एक्ज़िक्यूटिव',        1,  1,    180, 2),
    (gen_random_uuid(), NULL, 'Business Development Officer', 'बिज़नेस डेवलपमेंट ऑफिसर',            2,  2,    200, 3),
    (gen_random_uuid(), NULL, 'Business Manager',             'बिज़नेस मैनेजर',                     3,  5,    215, 4),
    (gen_random_uuid(), NULL, 'Assistant Sales Director',     'असिस्टेंट सेल्स डायरेक्टर',           6,  9,    225, 5),
    (gen_random_uuid(), NULL, 'Sales Director',               'सेल्स डायरेक्टर',                    10, 14,   235, 6),
    (gen_random_uuid(), NULL, 'Vice President',                'वाइस प्रेसिडेंट',                    15, 19,   245, 7),
    (gen_random_uuid(), NULL, 'President',                     'प्रेसिडेंट',                         20, NULL, 255, 8)
ON CONFLICT (COALESCE(org_id, '00000000-0000-0000-0000-000000000000'::uuid), name) WHERE deleted_at IS NULL DO NOTHING;

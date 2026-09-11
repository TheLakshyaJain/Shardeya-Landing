-- Illustrative starter rates for local dev/testing -- NOT a certified legal
-- data feed. Every row's source_note says so explicitly, and the
-- calculator's own response always carries effectiveFrom + a disclaimer
-- (M-08 §7: "rates are indicative, confirm with the sub-registrar") for
-- exactly this reason. A real deployment needs these replaced with actual
-- verified state government figures via the admin endpoint (M-14 basic)
-- this milestone adds -- seeding placeholder numbers here only unblocks
-- building and testing the *mechanism* (lookup, gender fallback, flat/pct/
-- cap registration semantics), not the legal accuracy of any single figure.
--
-- Deliberately mixes two shapes to exercise both lookup paths the service
-- layer must support: UP/RJ/MH/DL seed real MALE/FEMALE/JOINT variation
-- (most Indian states give women a rebate); MP/KA seed ONLY an ANY row,
-- so a MALE/FEMALE/JOINT lookup against them must exercise the "falling
-- back to gender=ANY" path (M-08 §7) for real, not just in theory.
INSERT INTO stamp_duty_rate (state_code, property_type, transaction_type, buyer_gender, stamp_duty_pct, registration_pct, registration_flat, registration_cap, effective_from, source_note) VALUES
    -- Uttar Pradesh
    ('UP', 'RESIDENTIAL', 'SALE', 'MALE',   7.0, 1.0, NULL, NULL, '2025-04-01', 'Illustrative placeholder rate -- verify with UP sub-registrar before relying on it.'),
    ('UP', 'RESIDENTIAL', 'SALE', 'FEMALE', 6.0, 1.0, NULL, NULL, '2025-04-01', 'Illustrative placeholder rate -- verify with UP sub-registrar before relying on it.'),
    ('UP', 'RESIDENTIAL', 'SALE', 'JOINT',  6.5, 1.0, NULL, NULL, '2025-04-01', 'Illustrative placeholder rate -- verify with UP sub-registrar before relying on it.'),
    ('UP', 'COMMERCIAL',  'SALE', 'ANY',    7.0, 1.0, NULL, NULL, '2025-04-01', 'Illustrative placeholder rate -- verify with UP sub-registrar before relying on it.'),
    -- Rajasthan
    ('RJ', 'RESIDENTIAL', 'SALE', 'MALE',   6.0, 1.0, NULL, NULL, '2025-04-01', 'Illustrative placeholder rate -- verify with RJ sub-registrar before relying on it.'),
    ('RJ', 'RESIDENTIAL', 'SALE', 'FEMALE', 4.0, 1.0, NULL, NULL, '2025-04-01', 'Illustrative placeholder rate -- verify with RJ sub-registrar before relying on it.'),
    ('RJ', 'RESIDENTIAL', 'SALE', 'JOINT',  5.0, 1.0, NULL, NULL, '2025-04-01', 'Illustrative placeholder rate -- verify with RJ sub-registrar before relying on it.'),
    ('RJ', 'COMMERCIAL',  'SALE', 'ANY',    6.0, 1.0, NULL, NULL, '2025-04-01', 'Illustrative placeholder rate -- verify with RJ sub-registrar before relying on it.'),
    -- Maharashtra (registration capped at Rs 30,000, a real, common state rule)
    ('MH', 'RESIDENTIAL', 'SALE', 'MALE',   6.0, 1.0, NULL, 30000, '2025-04-01', 'Illustrative placeholder rate -- verify with MH sub-registrar before relying on it.'),
    ('MH', 'RESIDENTIAL', 'SALE', 'FEMALE', 5.0, 1.0, NULL, 30000, '2025-04-01', 'Illustrative placeholder rate -- verify with MH sub-registrar before relying on it.'),
    ('MH', 'RESIDENTIAL', 'SALE', 'JOINT',  5.5, 1.0, NULL, 30000, '2025-04-01', 'Illustrative placeholder rate -- verify with MH sub-registrar before relying on it.'),
    -- Delhi
    ('DL', 'RESIDENTIAL', 'SALE', 'MALE',   6.0, 1.0, NULL, NULL, '2025-04-01', 'Illustrative placeholder rate -- verify with DL sub-registrar before relying on it.'),
    ('DL', 'RESIDENTIAL', 'SALE', 'FEMALE', 4.0, 1.0, NULL, NULL, '2025-04-01', 'Illustrative placeholder rate -- verify with DL sub-registrar before relying on it.'),
    ('DL', 'RESIDENTIAL', 'SALE', 'JOINT',  5.0, 1.0, NULL, NULL, '2025-04-01', 'Illustrative placeholder rate -- verify with DL sub-registrar before relying on it.'),
    -- Madhya Pradesh -- gender-neutral (ANY only), deliberately no
    -- MALE/FEMALE/JOINT rows, to exercise the fallback lookup path.
    ('MP', 'RESIDENTIAL', 'SALE', 'ANY',    7.5, 3.0, NULL, NULL, '2025-04-01', 'Illustrative placeholder rate -- verify with MP sub-registrar before relying on it.'),
    -- Karnataka -- gender-neutral (ANY only), same reason as MP.
    ('KA', 'RESIDENTIAL', 'SALE', 'ANY',    5.0, 1.0, NULL, NULL, '2025-04-01', 'Illustrative placeholder rate -- verify with KA sub-registrar before relying on it.')
ON CONFLICT (state_code, property_type, transaction_type, buyer_gender, effective_from) DO NOTHING;

-- Exact rows from 01-DATA-MODEL.md §2. Only BIGHA is state-dependent (UP and
-- RJ definitions differ); every other unit applies universally (state_code NULL).
INSERT INTO measurement_unit (code, name_en, name_hi, to_sqft_factor, state_code, is_active) VALUES
    ('SQ_FT', 'Square Feet', 'वर्ग फुट', 1.0, NULL, true),
    ('SQ_M', 'Square Metre', 'वर्ग मीटर', 10.7639, NULL, true),
    ('SQ_YD', 'Square Yard (Gaj)', 'गज', 9.0, NULL, true),
    ('ACRE', 'Acre', 'एकड़', 43560.0, NULL, true),
    ('BIGHA', 'Bigha', 'बीघा', 27000.0, 'UP', true),
    ('BIGHA', 'Bigha', 'बीघा', 27225.0, 'RJ', true),
    ('GUNTA', 'Gunta', 'गुंठा', 1089.0, NULL, true),
    ('DISMIL', 'Dismil', 'डिसमिल', 435.6, NULL, true)
ON CONFLICT (code, (coalesce(state_code, '--'))) DO NOTHING;

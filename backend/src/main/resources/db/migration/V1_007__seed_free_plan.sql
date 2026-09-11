-- Only FREE is seeded. PRO/PREMIUM pricing (price_monthly/price_yearly) isn't
-- specified anywhere in 00-05 — real numbers are an M-09 (Subscription
-- Payments) concern. Seeding placeholder prices now would risk being mistaken
-- for real pricing later, so those rows are deferred to whenever M-09 actually
-- defines them. M1 only needs FREE to exist, since signup always creates a
-- FREE subscription (05-MILESTONES.md M1: "subscription (seeded FREE)").
INSERT INTO plan (code, name_en, name_hi, price_monthly, price_yearly, sort_order, is_active)
VALUES ('FREE', 'Free', 'फ्री', 0.00, 0.00, 0, true)
ON CONFLICT (code) DO NOTHING;

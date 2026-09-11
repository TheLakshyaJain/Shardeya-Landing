-- B-14 §20.4 default tiers ("Bronze 0-2, Silver 3-9, Gold 10-24,
-- Platinum 25+") for every BUILDER org that existed before this milestone
-- -- new orgs get the identical 4 rows seeded at signup time instead
-- (AuthService.verifySignupOtp), the same "backfill existing + seed new
-- going forward" split M4's V4_010/V4_011 team-member-usage trigger already
-- established for this exact class of "per-org default data" problem.
-- ON CONFLICT is unnecessary here (unlike most idempotent seeds) since
-- broker_tier has no natural unique key across orgs to conflict on; the
-- WHERE NOT EXISTS guard below is what makes this migration itself
-- re-runnable/idempotent if Flyway's own history ever needed replaying
-- against a database that already has tiers for some orgs.
INSERT INTO broker_tier (org_id, name, name_hi, min_deals, max_deals, bonus_type, bonus_value, sort_order)
SELECT o.id, t.name, t.name_hi, t.min_deals, t.max_deals, 'NONE'::broker_tier_bonus_type, 0, t.sort_order
FROM organization o
CROSS JOIN (VALUES
    ('Bronze',   'ब्रॉन्ज़',  0,  2,   1),
    ('Silver',   'सिल्वर',    3,  9,   2),
    ('Gold',     'गोल्ड',     10, 24,  3),
    ('Platinum', 'प्लैटिनम',  25, NULL::INTEGER, 4)
) AS t(name, name_hi, min_deals, max_deals, sort_order)
WHERE o.type = 'BUILDER' AND o.deletion_requested_at IS NULL
  AND NOT EXISTS (SELECT 1 FROM broker_tier bt WHERE bt.org_id = o.id AND bt.deleted_at IS NULL);

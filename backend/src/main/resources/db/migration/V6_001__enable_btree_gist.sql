-- B-14 §20.4: broker_tier ranges (min_deals..max_deals) must not overlap
-- within an org. A GiST EXCLUDE constraint combining an equality check
-- (org_id) with a range-overlap check (&&) needs btree_gist's operator
-- classes to let a plain equality column participate in a GiST index at
-- all -- same "own migration, mirrors pg_trgm's V2_001 precedent" pattern
-- as every other extension this project has needed.
CREATE EXTENSION IF NOT EXISTS btree_gist;

-- Trigram indexes (plot_number fuzzy/partial match — "A-1" finding "A-12",
-- 01-DATA-MODEL.md §4 plot indexes) need pg_trgm. Never enabled before M2;
-- nothing prior needed fuzzy text search.
CREATE EXTENSION IF NOT EXISTS pg_trgm;

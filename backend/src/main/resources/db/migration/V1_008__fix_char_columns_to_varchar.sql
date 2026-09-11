-- M0's organization/app_user migrations used CHAR(n) (fixed-length, blank-padded)
-- for short codes (default_language, state_code, language). Hibernate maps a
-- Java String field to VARCHAR by default, so schema validation at startup
-- failed with "found bpchar, expecting varchar" the moment the JPA entity was
-- introduced in M1. CHAR's blank-padding is also rarely what you actually want
-- (silent trailing-space semantics). Can't edit the M0 migrations (CLAUDE.md:
-- never modify a committed migration) — fixing forward instead.
ALTER TABLE organization ALTER COLUMN default_language TYPE VARCHAR(2);
ALTER TABLE organization ALTER COLUMN state_code TYPE VARCHAR(2);
ALTER TABLE app_user ALTER COLUMN language TYPE VARCHAR(2);

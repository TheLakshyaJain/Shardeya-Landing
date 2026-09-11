-- 01-DATA-MODEL.md's app_user table doesn't list this column, but M-01 §10
-- ("Edge cases") requires it: "JWT carries token_version; incrementing it on
-- the user record invalidates outstanding access tokens within 15 min." This
-- is the mechanism behind password-change and account-lock forcing existing
-- access tokens to stop working without a separate revocation store.
ALTER TABLE app_user ADD COLUMN token_version SMALLINT NOT NULL DEFAULT 0;

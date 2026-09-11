-- Closes the FK debt V3_001's own comment named explicitly: "customer_id
-- and broker_partner_id have NO FK yet... Add the FKs when those tables
-- land." customer_id was closed in V4_003 once M-12 landed; broker_partner_id
-- closes here now that broker_partner exists. external_broker_name/
-- external_broker_mobile remain free-text with no FK -- B-14 §10's
-- "external broker (free text) -> no ledger automation" is a permanent
-- design, not FK debt to close later.
ALTER TABLE plot_sale ADD CONSTRAINT fk_plot_sale_broker_partner FOREIGN KEY (broker_partner_id) REFERENCES broker_partner(id);

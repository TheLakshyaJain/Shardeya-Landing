-- V3_001's own comment named this exact debt: "customer_id ... has NO FK
-- yet: customer (M-12) doesn't exist until later milestones ... Add the FK
-- when those tables land." The customer table now exists (V4_002).
ALTER TABLE plot_sale ADD CONSTRAINT fk_plot_sale_customer
    FOREIGN KEY (customer_id) REFERENCES customer(id);

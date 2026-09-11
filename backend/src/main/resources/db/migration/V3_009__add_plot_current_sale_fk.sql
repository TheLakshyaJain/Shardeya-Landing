-- plot_sale now exists (V3_001) -- add the FK V2_005 deferred.
ALTER TABLE plot ADD CONSTRAINT fk_plot_current_sale FOREIGN KEY (current_sale_id) REFERENCES plot_sale(id);

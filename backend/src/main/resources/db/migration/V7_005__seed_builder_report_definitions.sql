-- B-11 §17.1's nine builder reports. `required_permission` is the minimum
-- bar to see the report exist/preview it at all; PROJECT_SUMMARY/PLOT_INVENTORY/
-- SALES/STAFF_ACTIVITY require REPORT_VIEW_ALL (management-level, matching
-- M-10 §9 "Sales Exec -> own-scoped reports only" -- these four have no
-- owner-scoped shape at all, so Sales Exec is excluded outright, not scoped
-- down). COLLECTION/PENDING_COLLECTIONS/BROKER_COMMISSION require
-- REPORT_FINANCIAL (Accounts Staff's own gate). LEAD_CUSTOMER/FOLLOWUP_DUE
-- declare the lower REPORT_VIEW_OWN bar -- ReportService additionally checks
-- for REPORT_VIEW_ALL on just these two codes to decide ALL-vs-OWN scope,
-- the same "service-layer OR-check, not a declarative annotation" pattern
-- CustomerService/TrackerService already established for this exact shape
-- (see CLAUDE.md's Milestone 4 notes) -- Accounts Staff holds neither
-- REPORT_VIEW_ALL nor REPORT_VIEW_OWN, so these two correctly stay blocked
-- for them too, matching B-07 §9's "no lead access at all".
INSERT INTO report_definition (code, profile, name_en, name_hi, description_key, supported_filters, required_permission, sort_order) VALUES
('PROJECT_SUMMARY', 'BUILDER', 'Project Summary', 'प्रोजेक्ट सारांश', 'report.projectSummary.description',
 '[{"key":"projectId","type":"PROJECT"},{"key":"status","type":"SELECT","options":["UPCOMING","ACTIVE","COMPLETED"]},{"key":"from","type":"DATE"},{"key":"to","type":"DATE"}]',
 'REPORT_VIEW_ALL', 1),
('PLOT_INVENTORY', 'BUILDER', 'Plot Inventory', 'प्लॉट इन्वेंटरी', 'report.plotInventory.description',
 '[{"key":"projectId","type":"PROJECT"},{"key":"status","type":"SELECT","options":["AVAILABLE","RESERVED","SOLD"]},{"key":"facing","type":"SELECT","options":["N","S","E","W","NE","NW","SE","SW"]},{"key":"minSqft","type":"NUMBER"},{"key":"maxSqft","type":"NUMBER"}]',
 'REPORT_VIEW_ALL', 2),
('SALES', 'BUILDER', 'Sales', 'बिक्री', 'report.sales.description',
 '[{"key":"projectId","type":"PROJECT"},{"key":"from","type":"DATE"},{"key":"to","type":"DATE"},{"key":"brokerPartnerId","type":"BROKER"}]',
 'REPORT_VIEW_ALL', 3),
('COLLECTION', 'BUILDER', 'Collection', 'वसूली', 'report.collection.description',
 '[{"key":"projectId","type":"PROJECT"},{"key":"from","type":"DATE"},{"key":"to","type":"DATE"},{"key":"mode","type":"SELECT","options":["CASH","CHEQUE","BANK_TRANSFER","UPI","DD"]}]',
 'REPORT_FINANCIAL', 4),
('PENDING_COLLECTIONS', 'BUILDER', 'Pending Collections', 'लंबित वसूली', 'report.pendingCollections.description',
 '[{"key":"projectId","type":"PROJECT"},{"key":"overdueOnly","type":"BOOLEAN"}]',
 'REPORT_FINANCIAL', 5),
('BROKER_COMMISSION', 'BUILDER', 'Broker Commission', 'ब्रोकर कमीशन', 'report.brokerCommission.description',
 '[{"key":"brokerPartnerId","type":"BROKER"},{"key":"projectId","type":"PROJECT"},{"key":"from","type":"DATE"},{"key":"to","type":"DATE"}]',
 'REPORT_FINANCIAL', 6),
('LEAD_CUSTOMER', 'BUILDER', 'Lead / Customer', 'लीड / ग्राहक', 'report.leadCustomer.description',
 '[{"key":"projectId","type":"PROJECT"},{"key":"status","type":"SELECT","options":["INTERESTED","SITE_VISIT_SCHEDULED","SITE_VISIT_DONE","FOLLOWING_UP","DEAL_CLOSED","LOST"]},{"key":"source","type":"SELECT","options":["REFERRAL","FACEBOOK","INSTAGRAM","WALK_IN","COLD_CALL","WEBSITE","BROKER","EXHIBITION","SOCIAL_MEDIA","OTHER"]},{"key":"assignedTo","type":"STAFF"}]',
 'REPORT_VIEW_OWN', 7),
('FOLLOWUP_DUE', 'BUILDER', 'Follow-up Due', 'फॉलो-अप देय', 'report.followupDue.description',
 '[{"key":"assignedTo","type":"STAFF"},{"key":"from","type":"DATE"},{"key":"to","type":"DATE"}]',
 'REPORT_VIEW_OWN', 8),
('STAFF_ACTIVITY', 'BUILDER', 'Staff Activity', 'स्टाफ गतिविधि', 'report.staffActivity.description',
 '[{"key":"assignedTo","type":"STAFF"},{"key":"from","type":"DATE"},{"key":"to","type":"DATE"}]',
 'REPORT_VIEW_ALL', 9)
ON CONFLICT (code) DO NOTHING;

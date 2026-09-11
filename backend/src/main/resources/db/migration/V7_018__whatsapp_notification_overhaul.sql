-- Four-part WhatsApp notification overhaul (see CLAUDE.md's own writeup
-- for the full "why"):
--
-- 1. whatsapp_optin gains captured_by -- who (which staff/admin user)
--    ticked a buyer's consent on their behalf. Nullable: a SELF_SERVICE
--    row (a staff member OTP-confirming their own mobile) has no "captured
--    by" concept at all -- only BUILDER_CAPTURED rows ever populate this,
--    which is itself the visible, in-data distinction between "this
--    person proved they can receive a code" and "the builder attested
--    consent on this third party's behalf" the source column already
--    started, now made concrete with a real actor reference.
--
-- 2. COMMISSION_DUE gains WHATSAPP as a channel (alongside its existing
--    IN_APP+EMAIL) -- recipient narrowing to the org owner only is
--    enforced in NotificationDispatchService, not schema-expressible here.
--
-- 3. FOLLOWUP_DUE loses WHATSAPP entirely, back to IN_APP only. The type
--    itself is untouched (still fires, still shows in-app) -- only the
--    channel narrows.
ALTER TABLE whatsapp_optin ADD COLUMN captured_by UUID REFERENCES app_user(id);

UPDATE notification_type SET default_channels = ARRAY['IN_APP','EMAIL','WHATSAPP']
    WHERE code = 'COMMISSION_DUE';
UPDATE notification_type SET default_channels = ARRAY['IN_APP']
    WHERE code = 'FOLLOWUP_DUE';

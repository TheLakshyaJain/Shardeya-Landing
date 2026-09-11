-- Add latitude and longitude coordinates for map pin location (B-02 Project Map Integration)
ALTER TABLE project
    ADD COLUMN IF NOT EXISTS latitude NUMERIC(10, 7),
    ADD COLUMN IF NOT EXISTS longitude NUMERIC(10, 7);

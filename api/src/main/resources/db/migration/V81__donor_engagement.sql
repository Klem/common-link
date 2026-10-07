-- Timestamp of the donor's last visit to the engagement feed ("Depuis votre dernière visite").
-- Null for every donor until they first open the feed after this sprint ships -- in that case the
-- feed shows everything available rather than nothing (see DonorEngagementService.getFeed).
ALTER TABLE donor_profiles ADD COLUMN last_seen_at TIMESTAMPTZ;

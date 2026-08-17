-- campaigns.raised existed since V9 but no code ever incremented it: donation confirmation only
-- ever wrote donations.confirmed_at. Every reader of the column (campaign DTOs, public widget
-- progress bar, next-milestone query, and now the collection-cap check) was therefore reading a
-- value frozen at insert time.
--
-- DonationService.confirmDonation now maintains it. This backfill realigns existing rows with the
-- confirmed donations already recorded, so the cap check does not start from a wrong baseline.
UPDATE campaigns c
SET raised = COALESCE(
        (SELECT SUM(d.amount)
         FROM donations d
         WHERE d.campaign_id = c.id
           AND d.confirmed_at IS NOT NULL),
        0);

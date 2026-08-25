-- Opaque, donor-facing correlation id for a donation's Mollie round trip.
--
-- Minted by PublicWidgetService BEFORE calling Mollie (Mollie's own payment id does not exist yet
-- at redirect-URL construction time, so it cannot be embedded in that URL). Carried as a query
-- param on the redirect URL and read back by the /return page to poll GET
-- /api/public/widget/donations/{ref}/status without ever exposing provider_ref or donor/campaign
-- internal IDs publicly.
--
-- Nullable: only donations created through the public widget flow have one; donations recorded by
-- other paths (reconciler, future channels) leave it null.
ALTER TABLE donations
    ADD COLUMN public_ref UUID;

COMMENT ON COLUMN donations.public_ref IS
    'Opaque correlation id handed to the donor via the Mollie redirect URL; looked up by the public /status polling endpoint.';

-- Partial: most rows outside the widget flow will never have one (jpa-rules: index nullable
-- lookup columns as PARTIAL).
CREATE UNIQUE INDEX donations_public_ref_unique ON donations (public_ref) WHERE public_ref IS NOT NULL;

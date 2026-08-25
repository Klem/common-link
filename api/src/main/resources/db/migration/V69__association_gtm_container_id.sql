-- Google Tag Manager container ID for Ad Grants tracking. Drives injection on the landing page,
-- the embedded widget, and the copy/paste export in the settings tab — not landing-scoped, hence
-- no `landing_` prefix despite being configured from the Landing settings tab.
ALTER TABLE association_profiles
    ADD COLUMN gtm_container_id VARCHAR(20);

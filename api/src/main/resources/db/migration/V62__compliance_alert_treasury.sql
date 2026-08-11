-- V62 : Champs de traçabilité de la notification à la DG Trésor (épique E4, prompt 17).
-- La notification est un geste humain hors application ; ces colonnes en conservent la preuve.
ALTER TABLE compliance_alert
    ADD COLUMN treasury_notified_at         TIMESTAMPTZ,
    ADD COLUMN treasury_notification_method VARCHAR(128),
    ADD COLUMN treasury_notification_ref    VARCHAR(256);

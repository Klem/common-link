-- V60 : Contrôle de gel à la création du don (épique E4, tâche Asana 1216210853624506).
-- Étend le CHECK de compliance_audit_log.subject_type pour accepter DONOR.
--
-- Les donateurs sont des personnes physiques distinctes des associations et de leurs représentants.
-- Un type sujet dédié permet de tracer chaque événement de filtrage d'un donateur vers son
-- DonorProfile.id, sans ambiguïté avec DONATION (qui identifie un don déjà persisté — aucun don
-- n'existe encore au moment du contrôle de gel, qui précède la création du paiement Mollie).
--
-- Rollback : U60__donor_freeze_screening.sql
ALTER TABLE compliance_audit_log DROP CONSTRAINT compliance_audit_log_subject_type_check;
ALTER TABLE compliance_audit_log
    ADD CONSTRAINT compliance_audit_log_subject_type_check
        CHECK (subject_type IN ('ASSOCIATION', 'DONATION', 'CAMPAIGN', 'ALERT', 'DECLARANT', 'SYSTEM', 'BENEFICIAL_OWNER', 'DONOR'));

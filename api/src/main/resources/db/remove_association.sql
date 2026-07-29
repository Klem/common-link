-- =============================================================
-- CommonLink — remove_association.sql
-- Completely removes one or more associations and everything
-- linked to them.
-- Never use in production — associations are never deleted in production.
-- =============================================================
-- Usage
--   • List the UUID(s) (users.id) in v_target_user_ids.
--
-- What is deleted for each targeted association:
--   payouts, donations (to its campaigns), related onchain_jobs,
--   campaigns + milestones + budgets, payees, IBANs,
--   Monerium/Mollie connections (cascade from association_profiles),
--   fiscal mandates, KYC documents, registry checks,
--   magic_link_tokens, refresh_tokens, email_verification_tokens,
--   the association_profiles row and the users account.
--
-- Donors who contributed to these campaigns are preserved;
-- only their donations to those campaigns are removed.
--
-- Safety: everything runs in a transaction. On error → full rollback.
-- =============================================================

DO $$
    DECLARE
        -- ── UUID(s) (users.id) of the associations to fully remove ──
        v_target_user_ids UUID[] := ARRAY[
            -- 'xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx'
        ]::UUID[];

        -- Computed automatically
        v_target_assoc_ids   UUID[];
        v_target_emails      TEXT[];
        v_onchain_job_ids    UUID[];
        v_donation_corr_keys TEXT[];

        v_deleted BIGINT;
    BEGIN

        IF array_length(v_target_user_ids, 1) IS NULL THEN
            RAISE EXCEPTION 'v_target_user_ids is empty — nothing to delete.';
        END IF;

        -- ── Resolve targeted profiles / emails ───────────────────────
        SELECT array_agg(ap.id) INTO v_target_assoc_ids
        FROM association_profiles ap WHERE ap.user_id = ANY(v_target_user_ids);

        SELECT array_agg(u.email) INTO v_target_emails
        FROM users u WHERE u.id = ANY(v_target_user_ids);

        v_target_assoc_ids := COALESCE(v_target_assoc_ids, ARRAY[]::UUID[]);
        v_target_emails    := COALESCE(v_target_emails,    ARRAY[]::TEXT[]);

        RAISE NOTICE 'Deleting: % user(s), % association(s).',
            array_length(v_target_user_ids, 1),
            array_length(v_target_assoc_ids, 1);

        -- ════════════════════════════════════════════════════════════
        -- PRE-COLLECTION  (before deleting parent rows)
        --   onchain_jobs have no FK — collect their IDs now,
        --   while payouts and donations still exist.
        -- ════════════════════════════════════════════════════════════
        SELECT array_agg(DISTINCT p.onchain_job_id) INTO v_onchain_job_ids
        FROM payouts p
        JOIN campaigns c ON c.id = p.campaign_id
        WHERE c.association_id = ANY(v_target_assoc_ids)
          AND p.onchain_job_id IS NOT NULL;

        SELECT array_agg('DONATION:' || d.id::TEXT) INTO v_donation_corr_keys
        FROM donations d
        JOIN campaigns c ON c.id = d.campaign_id
        WHERE c.association_id = ANY(v_target_assoc_ids);

        v_onchain_job_ids    := COALESCE(v_onchain_job_ids,    ARRAY[]::UUID[]);
        v_donation_corr_keys := COALESCE(v_donation_corr_keys, ARRAY[]::TEXT[]);

        -- ════════════════════════════════════════════════════════════
        -- 1. PAYOUTS  (FK campaign_id with no ON DELETE CASCADE)
        -- ════════════════════════════════════════════════════════════
        DELETE FROM payouts p
        WHERE EXISTS (
            SELECT 1 FROM campaigns c
            WHERE c.id = p.campaign_id
              AND c.association_id = ANY(v_target_assoc_ids)
        );
        GET DIAGNOSTICS v_deleted = ROW_COUNT;  RAISE NOTICE 'payouts deleted: %', v_deleted;

        -- ════════════════════════════════════════════════════════════
        -- 2. DONATIONS to the targeted campaigns
        --    (donation_receipts removed via ON DELETE CASCADE)
        -- ════════════════════════════════════════════════════════════
        DELETE FROM donations d
        WHERE EXISTS (
            SELECT 1 FROM campaigns c
            WHERE c.id = d.campaign_id
              AND c.association_id = ANY(v_target_assoc_ids)
        );
        GET DIAGNOSTICS v_deleted = ROW_COUNT;  RAISE NOTICE 'donations deleted: %', v_deleted;

        -- ════════════════════════════════════════════════════════════
        -- 3. ONCHAIN JOBS  (no FK — delete by pre-collected IDs)
        -- ════════════════════════════════════════════════════════════
        DELETE FROM onchain_jobs j
        WHERE j.id = ANY(v_onchain_job_ids)
           OR j.correlation_key = ANY(v_donation_corr_keys);
        GET DIAGNOSTICS v_deleted = ROW_COUNT;  RAISE NOTICE 'onchain_jobs deleted: %', v_deleted;

        -- ════════════════════════════════════════════════════════════
        -- 4. CAMPAIGNS  (cascade → milestones, budget_sections,
        --    budget_items)
        -- ════════════════════════════════════════════════════════════
        DELETE FROM campaigns c
        WHERE c.association_id = ANY(v_target_assoc_ids);
        GET DIAGNOSTICS v_deleted = ROW_COUNT;  RAISE NOTICE 'campaigns deleted: %', v_deleted;

        -- ════════════════════════════════════════════════════════════
        -- 5. MAGIC LINK TOKENS  (no FK — keyed by email)
        -- ════════════════════════════════════════════════════════════
        DELETE FROM magic_link_tokens m
        WHERE m.email = ANY(v_target_emails);
        GET DIAGNOSTICS v_deleted = ROW_COUNT;  RAISE NOTICE 'magic_link_tokens deleted: %', v_deleted;

        -- ════════════════════════════════════════════════════════════
        -- 6. REGISTRY CHECKS  (circular FK — V36)
        --    a) Null out checked_by to release FK → users
        --    b) Null out decision_registry_check_id to release the
        --       circular FK on association_profiles
        --    c) Delete the registry checks for these associations
        -- ════════════════════════════════════════════════════════════
        UPDATE association_registry_check
        SET checked_by = NULL
        WHERE association_id = ANY(v_target_assoc_ids);

        UPDATE association_profiles ap
        SET decision_registry_check_id = NULL
        WHERE ap.id = ANY(v_target_assoc_ids);

        DELETE FROM association_registry_check arc
        WHERE arc.association_id = ANY(v_target_assoc_ids);
        GET DIAGNOSTICS v_deleted = ROW_COUNT;  RAISE NOTICE 'association_registry_check deleted: %', v_deleted;

        -- ════════════════════════════════════════════════════════════
        -- 7. FISCAL MANDATES & KYC DOCUMENTS  (V35 — no CASCADE)
        -- ════════════════════════════════════════════════════════════
        DELETE FROM fiscal_mandate fm
        WHERE fm.association_id = ANY(v_target_assoc_ids);
        GET DIAGNOSTICS v_deleted = ROW_COUNT;  RAISE NOTICE 'fiscal_mandate deleted: %', v_deleted;

        DELETE FROM association_document ad
        WHERE ad.association_id = ANY(v_target_assoc_ids);
        GET DIAGNOSTICS v_deleted = ROW_COUNT;  RAISE NOTICE 'association_document deleted: %', v_deleted;

        -- ════════════════════════════════════════════════════════════
        -- 8. ASSOCIATION PROFILES  (cascade → payees, payee_ibans,
        --    monerium_connections, monerium_oauth_states, Mollie rows)
        -- ════════════════════════════════════════════════════════════
        DELETE FROM association_profiles ap
        WHERE ap.id = ANY(v_target_assoc_ids);
        GET DIAGNOSTICS v_deleted = ROW_COUNT;  RAISE NOTICE 'association_profiles deleted: %', v_deleted;

        -- ════════════════════════════════════════════════════════════
        -- 9. USERS  (cascade → refresh_tokens, email_verification_tokens)
        -- ════════════════════════════════════════════════════════════
        DELETE FROM users u
        WHERE u.id = ANY(v_target_user_ids);
        GET DIAGNOSTICS v_deleted = ROW_COUNT;  RAISE NOTICE 'users deleted: %', v_deleted;

        RAISE NOTICE 'Deletion complete.';
    END $$;

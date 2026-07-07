-- =============================================================
-- CommonLink — cleanup_db.sql
-- Purge des données applicatives, avec exclusion optionnelle d'utilisateurs.
-- =============================================================
-- Usage
--   • Tout nettoyer            : laisser v_keep_user_ids vide (ARRAY[]::UUID[]).
--   • Garder certains users    : lister leurs UUID dans v_keep_user_ids.
--
-- Ce qui est préservé pour un user gardé :
--   son compte users + le profil lié (donor_profile OU association_profile)
--   + toute la chaîne dépendante de ce profil :
--       - donateur     : ses donations
--       - association  : ses campagnes, milestones, budgets, payees, IBANs,
--                        payouts, connexions Monerium
--   + ses tokens (refresh / email / magic-link par email).
--
-- Le nettoyage s'appuie sur les ON DELETE CASCADE du schéma : supprimer une
-- ligne users ou association_profiles efface automatiquement ses enfants.
-- On supprime donc au niveau racine, en épargnant les racines gardées.
--
-- Sécurité : tout est dans une transaction. En cas d'erreur → rollback total.
-- =============================================================

DO $$
    DECLARE
        -- ── UUID d'utilisateurs à EXCLURE de la purge (table users.id) ──
        -- Exemple : ARRAY['a0000000-0000-0000-0000-000000000001']::UUID[]
--         v_keep_user_ids UUID[] := ARRAY['22d7e09f-1c5d-4595-bc27-fcbe2ffd079c','5a3245cb-b492-4a90-9439-8a020edfb7de','fe61fe1c-8c2f-4afc-a803-5b7d8710780a']::UUID[];
        v_keep_user_ids UUID[] := ARRAY[]::UUID[];

        -- Profils dérivés des users gardés (calculés automatiquement)
        v_keep_assoc_ids UUID[];
        v_keep_donor_ids UUID[];
        v_keep_emails    TEXT[];

        v_deleted BIGINT;
    BEGIN

        -- ── Résolution des profils/emails à conserver ────────────────
        SELECT array_agg(ap.id)        INTO v_keep_assoc_ids
        FROM association_profiles ap   WHERE ap.user_id = ANY(v_keep_user_ids);

        SELECT array_agg(dp.id)        INTO v_keep_donor_ids
        FROM donor_profiles dp         WHERE dp.user_id = ANY(v_keep_user_ids);

        SELECT array_agg(u.email)      INTO v_keep_emails
        FROM users u                   WHERE u.id = ANY(v_keep_user_ids);

        -- Normaliser les NULL (aucun match) en tableaux vides
        v_keep_assoc_ids := COALESCE(v_keep_assoc_ids, ARRAY[]::UUID[]);
        v_keep_donor_ids := COALESCE(v_keep_donor_ids, ARRAY[]::UUID[]);
        v_keep_emails    := COALESCE(v_keep_emails,    ARRAY[]::TEXT[]);

        RAISE NOTICE 'Conservation : % user(s), % association(s), % donateur(s).',
            array_length(v_keep_user_ids,1), array_length(v_keep_assoc_ids,1), array_length(v_keep_donor_ids,1);

        -- ════════════════════════════════════════════════════════════
        -- 1. PAYOUTS  (pas de cascade depuis campaigns → suppr. explicite)
        --    On garde ceux des campagnes d'associations conservées.
        -- ════════════════════════════════════════════════════════════
        DELETE FROM payouts p
        WHERE NOT EXISTS (
            SELECT 1 FROM campaigns c
            WHERE c.id = p.campaign_id
              AND c.association_id = ANY(v_keep_assoc_ids)
        );
        GET DIAGNOSTICS v_deleted = ROW_COUNT;  RAISE NOTICE 'payouts supprimés : %', v_deleted;

        -- ════════════════════════════════════════════════════════════
        -- 2. ONCHAIN JOBS  (table indépendante, aucune FK → purge globale)
        --    On ne peut pas la rattacher de façon fiable à un user gardé,
        --    donc on conserve uniquement les jobs liés à un payout conservé.
        -- ════════════════════════════════════════════════════════════
        DELETE FROM onchain_jobs j
        WHERE NOT EXISTS (
            SELECT 1 FROM payouts p WHERE p.onchain_job_id = j.id
        );
        GET DIAGNOSTICS v_deleted = ROW_COUNT;  RAISE NOTICE 'onchain_jobs supprimés : %', v_deleted;

        -- ════════════════════════════════════════════════════════════
        -- 3. DONATIONS
        --    Cascade depuis donor_profiles ET campaigns. On supprime ici
        --    celles qui ne sont rattachées NI à un donateur gardé NI à une
        --    campagne d'asso gardée (sinon la cascade users s'en charge,
        --    mais on évite de garder une donation "orpheline" d'un donateur
        --    gardé vers une campagne purgée — choix : on garde la donation
        --    tant que son donateur est conservé).
        -- ════════════════════════════════════════════════════════════
        DELETE FROM donations d
        WHERE d.donor_id <> ALL(v_keep_donor_ids)
          AND NOT EXISTS (
            SELECT 1 FROM campaigns c
            WHERE c.id = d.campaign_id AND c.association_id = ANY(v_keep_assoc_ids)
        );
        GET DIAGNOSTICS v_deleted = ROW_COUNT;  RAISE NOTICE 'donations supprimées : %', v_deleted;

        -- ════════════════════════════════════════════════════════════
        -- 4. CAMPAGNES  (cascade → milestones, budget_sections → budget_items)
        --    Gardées si l'association est conservée. Les sections/lignes de budget
        --    sont supprimées automatiquement via ON DELETE CASCADE.
        --    payees / payee_ibans cascadent depuis association_profiles (étape 6).
        -- ════════════════════════════════════════════════════════════
        DELETE FROM campaigns c
        WHERE c.association_id <> ALL(v_keep_assoc_ids);
        GET DIAGNOSTICS v_deleted = ROW_COUNT;  RAISE NOTICE 'campagnes supprimées : %', v_deleted;

        -- ════════════════════════════════════════════════════════════
        -- 5. TOKENS rattachés aux users NON gardés
        --    refresh / email_verification : FK user_id ON DELETE CASCADE
        --    (la cascade users à l'étape 7 suffit) — on nettoie tout de même
        --    les magic_link_tokens qui n'ont pas de FK (clé = email).
        -- ════════════════════════════════════════════════════════════
        DELETE FROM magic_link_tokens m
        WHERE m.email <> ALL(v_keep_emails);
        GET DIAGNOSTICS v_deleted = ROW_COUNT;  RAISE NOTICE 'magic_link_tokens supprimés : %', v_deleted;

        -- ════════════════════════════════════════════════════════════
        -- 6. PROFILS  (association_profiles cascade → payees, ibans, monerium,
        --    campagnes restantes ; donor_profiles cascade → donations restantes)
        --    On supprime les profils dont le user n'est pas gardé.
        -- ════════════════════════════════════════════════════════════
        DELETE FROM association_profiles ap WHERE ap.user_id <> ALL(v_keep_user_ids);
        GET DIAGNOSTICS v_deleted = ROW_COUNT;  RAISE NOTICE 'association_profiles supprimés : %', v_deleted;

        DELETE FROM donor_profiles dp WHERE dp.user_id <> ALL(v_keep_user_ids);
        GET DIAGNOSTICS v_deleted = ROW_COUNT;  RAISE NOTICE 'donor_profiles supprimés : %', v_deleted;

        -- ════════════════════════════════════════════════════════════
        -- 7. USERS  (cascade → refresh_tokens, email_verification_tokens,
        --    et profils déjà traités)
        -- ════════════════════════════════════════════════════════════
        DELETE FROM users u WHERE u.id <> ALL(v_keep_user_ids);
        GET DIAGNOSTICS v_deleted = ROW_COUNT;  RAISE NOTICE 'users supprimés : %', v_deleted;

        -- ════════════════════════════════════════════════════════════
        -- 8. RESYNC campaigns.raised (utile si on a gardé une asso mais
        --    purgé des donations de donateurs non conservés)
        -- ════════════════════════════════════════════════════════════
        UPDATE campaigns c
        SET raised = (SELECT COALESCE(SUM(d.amount),0) FROM donations d
                      WHERE d.campaign_id = c.id AND d.confirmed_at IS NOT NULL),
            updated_at = now();

        RAISE NOTICE 'Nettoyage terminé.';
    END $$;

-- =============================================================
-- VARIANTE « tout raser » (ignore les exclusions, remet à zéro).
-- Décommenter pour un reset total et instantané du schéma.
-- =============================================================
-- TRUNCATE TABLE
--   payouts, onchain_jobs, donations,
--   campaign_budget_items, campaign_budget_sections, campaign_milestones,
--   campaigns, payee_ibans, payees,
--   monerium_oauth_states, monerium_connections,
--   association_profiles, donor_profiles,
--   magic_link_tokens, email_verification_tokens, refresh_tokens,
--   users
-- RESTART IDENTITY CASCADE;
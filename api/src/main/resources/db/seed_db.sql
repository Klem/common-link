-- =============================================================
-- CommonLink — Dev Seed Data
-- Run once on a clean (migrated) database (through V32).
-- =============================================================
-- Hashes below are real BCrypt ($2a, cost 12) — no edit needed.
--   Test1234!  -> association
--   Donor1234! -> all donors
--
-- Accounts created
--   Association : test@commonlink.dev          / Test1234!
--   Named donors: sophie.martin@example.com …  / Donor1234!
--   Anon donors : anon21@seed.local …          / Donor1234!
--
-- Coverage
--   1   association + profile (verified)
--   12  campaigns      (LIVE · ENDED · DRAFT mix)
--   ~36 campaign_milestones (3 per non-draft campaign)
--   5   payees COMPANY + IBANs (mixed VOP statuses)
--   3   payees PERSON  (intervenants) + IBANs (VERIFIED)
--   50  donors          (20 named · 30 anonymous)
--   ~600 donations       over the last 3 months (90 % confirmed)
--   ~25 payouts          on LIVE/ENDED campaigns (+ backing onchain_jobs)
--
-- Idempotent-ish: this block does NOT delete first. Run on a clean DB,
-- or TRUNCATE the tables below before re-running.
-- =============================================================

DO $$
    DECLARE
        -- ── Real BCrypt hashes ($2a$12) ──────────────────────────────
        HASH_ASSOC CONSTANT TEXT := '$2a$12$/UbKgklk1NpJKBOQjRZfLe1Q3/ii58JXDaTmHfS5FTZaXyNdXYSYG';
        HASH_DONOR CONSTANT TEXT := '$2a$12$V01w7Ye/GHf5VDtGTmlF8eBNlmyDkHX/4vxjSvH.t76Fagk6A8cP6';

        -- ── Association ──────────────────────────────────────────────
        v_assoc_user_id UUID := 'a0000000-0000-0000-0000-000000000001';
        v_assoc_id      UUID := 'a0000000-0000-0000-0000-000000000002';

        -- ── Campaigns ────────────────────────────────────────────────
        v_camp UUID[] := ARRAY[
            'c0000000-0000-0000-0000-000000000001'::UUID,   --  1 LIVE  École pour tous
            'c0000000-0000-0000-0000-000000000002'::UUID,   --  2 LIVE  Aide alimentaire
            'c0000000-0000-0000-0000-000000000003'::UUID,   --  3 LIVE  Rénovation centre
            'c0000000-0000-0000-0000-000000000004'::UUID,   --  4 LIVE  Bourses étudiantes
            'c0000000-0000-0000-0000-000000000005'::UUID,   --  5 ENDED Plantation arbres
            'c0000000-0000-0000-0000-000000000006'::UUID,   --  6 ENDED Matériel médical
            'c0000000-0000-0000-0000-000000000007'::UUID,   --  7 LIVE  Soutien réfugiés
            'c0000000-0000-0000-0000-000000000008'::UUID,   --  8 ENDED Festival culturel
            'c0000000-0000-0000-0000-000000000009'::UUID,   --  9 DRAFT Maison de retraite
            'c0000000-0000-0000-0000-000000000010'::UUID,   -- 10 DRAFT Jardins partagés
            'c0000000-0000-0000-0000-000000000011'::UUID,   -- 11 LIVE  Familles monoparentales
            'c0000000-0000-0000-0000-000000000012'::UUID    -- 12 LIVE  Formation pro
            ];

        -- Non-draft campaign slots (eligible for donations + payouts)
        v_active_slots INT[] := ARRAY[1,2,3,4,5,6,7,8,11,12];
        -- ENDED + LIVE campaigns that already disbursed funds (eligible for payouts)
        v_payout_slots INT[] := ARRAY[1,2,3,5,6,7,8,11];

        -- ── Payees (COMPANY) ─────────────────────────────────────────
        v_payee_ids      UUID[];
        v_payee_iban_ids UUID[];
        v_payee_names TEXT[] := ARRAY[
            'Imprimerie Solidaire SARL',
            'Banque Alimentaire IDF',
            'BTP Rénov''Accès',
            'Reboise & Cie',
            'MedExport Logistique'
            ];
        v_payee_id1 TEXT[] := ARRAY['812345678','823456789','834567890','845678901','856789012'];
        v_payee_iban TEXT[] := ARRAY[
            'FR7630006000011234567890189',
            'FR7610107001011234567890129',
            'FR7630004000031234567890143',
            'FR7612548029981234567890161',
            'FR7630003035301234567890150'
            ];
        v_payee_iban_status TEXT[] := ARRAY['VERIFIED','VERIFIED','CLOSE_MATCH','VERIFIED','PENDING'];
        v_payee_vop TEXT[] := ARRAY['MATCH','MATCH','CLOSE_MATCH','MATCH',NULL];

        -- ── Payees (PERSON) ──────────────────────────────────────────
        v_person_ids      UUID[];
        v_person_iban_ids UUID[];
        v_person_names TEXT[] := ARRAY[
            'Marie Leclerc',
            'Jean-Paul Moreau',
            'Fatou Diallo'
            ];
        v_person_iban TEXT[] := ARRAY[
            'FR7614508590001234567890142',
            'FR7620041010051234567890183',
            'FR7610278060001234567890171'
            ];
        v_person_slots   INT[]     := ARRAY[1,3,7,11];
        v_person_amounts NUMERIC[] := ARRAY[350.00, 480.00, 290.00];

        -- ── Budget template (commun à toutes les campagnes) ──────────
        -- 4 lignes de CHARGES (EXPENSE) dont les fractions somment à 1.00
        -- → total EXPENSE == goal de la campagne. Les payouts sont tirés
        --   de ces lignes (label/type_code/kind cohérents).
        -- 2 lignes de PRODUITS (REVENUE) : dons en ligne (80 %) + subventions (20 %).
        v_exp_frac  NUMERIC[] := ARRAY[0.45, 0.25, 0.20, 0.10];
        v_exp_label TEXT[]    := ARRAY[
            'Achat de fournitures et matériel',
            'Frais de transport et logistique',
            'Prestations de services',
            'Indemnités des intervenants'
            ];
        v_exp_code  TEXT[]    := ARRAY['SUPPLIES','LOGISTICS','SERVICES','STAFF_COMP'];
        -- kind par ligne : la dernière (indemnités) est une RÉMUNÉRATION, le reste EXPENSE
        v_exp_kind  TEXT[]    := ARRAY['EXPENSE','EXPENSE','EXPENSE','REMUNERATION'];

        v_rev_frac  NUMERIC[] := ARRAY[0.80, 0.20];
        v_rev_label TEXT[]    := ARRAY['Dons en ligne','Subventions et mécénat'];
        v_rev_code  TEXT[]    := ARRAY['ONLINE_DONATIONS','GRANTS'];

        -- IDs des lignes EXPENSE, aplaties : index = (slot-1)*4 + k  (slot 1..12, k 1..4)
        v_exp_item_ids UUID[]    := ARRAY_FILL(NULL::UUID,    ARRAY[48]);
        v_exp_item_amt NUMERIC[] := ARRAY_FILL(0::NUMERIC,    ARRAY[48]);
        v_sec_id       UUID;
        v_item_id      UUID;
        v_eidx         INT;

        -- ── Donor state ──────────────────────────────────────────────
        v_donor_user_ids UUID[];
        v_donor_ids      UUID[];
        v_donor_names TEXT[] := ARRAY[
            -- 1-20 named donors
            'Sophie Martin',     'Thomas Bernard',   'Clara Dubois',      'Lucas Petit',
            'Emma Leroy',        'Hugo Moreau',       'Léa Simon',         'Nathan Laurent',
            'Camille Michel',    'Théo Lefebvre',     'Manon Garcia',      'Julien Martinez',
            'Chloé Roux',        'Antoine David',     'Inès Bertrand',     'Baptiste Morel',
            'Jade Thomas',       'Raphaël Picard',    'Lucie Durand',      'Maxime Girard',
            -- 21-50 anonymous donors (names stored but hidden by anonymous=true)
            'Élodie Faure',      'Sébastien Renard',  'Pauline Blanchard', 'Quentin Chevallier',
            'Margot Boyer',      'Florian Guerin',    'Anaïs Gauthier',    'Romain Lemaire',
            'Aurélie Perez',     'Vincent Dupont',    'Laura Giraud',      'Nicolas Fontaine',
            'Céline Lambert',    'Alexis Rousseau',   'Sandra Morin',      'Kevin Bonnet',
            'Nathalie Henry',    'Matthieu Colin',    'Isabelle Mercier',  'Franck Blanc',
            'Sylvie Barbier',    'Christophe Conte',  'Valérie Meunier',   'Stéphane Prévost',
            'Delphine Perrin',   'Olivier Leclercq',  'Véronique Tessier', 'Patrick Vasseur',
            'Brigitte Gilles',   'Michel Schneider'
            ];
        v_donor_anon BOOLEAN[] := ARRAY[
            FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,  -- 1-10
            FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,  -- 11-20
            TRUE, TRUE, TRUE, TRUE, TRUE, TRUE, TRUE, TRUE, TRUE, TRUE,   -- 21-30
            TRUE, TRUE, TRUE, TRUE, TRUE, TRUE, TRUE, TRUE, TRUE, TRUE,   -- 31-40
            TRUE, TRUE, TRUE, TRUE, TRUE, TRUE, TRUE, TRUE, TRUE, TRUE    -- 41-50
            ];

        -- ── Milestone fractions of goal ──────────────────────────────
        v_ms_frac NUMERIC[] := ARRAY[0.25, 0.50, 1.00];
        v_ms_emoji TEXT[]   := ARRAY['🌱','🌿','🌳'];
        v_ms_title TEXT[]   := ARRAY['Premier palier','Mi-parcours','Objectif atteint'];

        -- ── Loop vars ────────────────────────────────────────────────
        i           INT;
        j           INT;
        k           INT;
        slot        INT;
        n_donations INT;
        n_payouts   INT;
        rand_camp   INT;
        rand_amount NUMERIC(12,2);
        don_date    TIMESTAMPTZ;
        confirmed   TIMESTAMPTZ;
        pref_id     TEXT;
        v_uid       UUID;
        v_did       UUID;
        v_email     TEXT;
        v_goal      NUMERIC(12,2);
        v_status    TEXT;
        v_ms_status TEXT;
        v_ms_reached TIMESTAMPTZ;
        v_pidx      INT;
        v_job_id    UUID;
        v_payout_id UUID;
        v_payout_status TEXT;
        v_payout_conf   TIMESTAMPTZ;

    BEGIN

        -- ════════════════════════════════════════════════════════════
        -- 1. ASSOCIATION
        -- ════════════════════════════════════════════════════════════
        INSERT INTO users (id, email, role, provider, password_hash, display_name, email_verified, created_at, updated_at)
        VALUES (
                   v_assoc_user_id, 'test@commonlink.dev',
                   'ASSOCIATION', 'EMAIL', HASH_ASSOC,
                   'Fondation Lumière', TRUE,
                   '2026-01-10 09:00:00+01', '2026-01-10 09:00:00+01'
               );

        INSERT INTO association_profiles (id, user_id, name, identifier, city, postal_code, contact_name, description, verified)
        VALUES (
                   v_assoc_id, v_assoc_user_id,
                   'Fondation Lumière', '123456789',
                   'Paris', '75008', 'Marie Dupont',
                   'Association dédiée à l''éducation, l''aide humanitaire et le développement durable en France et à l''étranger.',
                   TRUE
               );

        -- ════════════════════════════════════════════════════════════
        -- 2. CAMPAIGNS  (no `reason` column — dropped)
        -- ════════════════════════════════════════════════════════════
        INSERT INTO campaigns
        (id, association_id, name, emoji, description, goal, raised, status, start_date, end_date, budget_hash, created_at, updated_at)
        VALUES
            (v_camp[1],  v_assoc_id, 'École pour tous', '🎓',
             'Financement de matériel pédagogique et de bourses pour enfants défavorisés dans les zones rurales.',
             15000, 0, 'LIVE', '2026-03-01', NULL,
             '0x' || repeat('1', 64),
             '2026-02-20 10:00:00+01', '2026-02-20 10:00:00+01'),

            (v_camp[2],  v_assoc_id, 'Aide alimentaire d''urgence', '🍽️',
             'Distribution de colis alimentaires d''urgence aux familles en grande précarité de la région parisienne.',
             5000, 0, 'LIVE', '2026-03-15', NULL,
             '0x' || repeat('2', 64),
             '2026-03-10 09:00:00+01', '2026-03-10 09:00:00+01'),

            (v_camp[3],  v_assoc_id, 'Rénovation du centre communautaire', '🏠',
             'Travaux de mise aux normes et d''accessibilité PMR du centre communautaire du 18e arrondissement.',
             25000, 0, 'LIVE', '2026-04-01', NULL,
             '0x' || repeat('3', 64),
             '2026-03-25 14:00:00+01', '2026-03-25 14:00:00+01'),

            (v_camp[4],  v_assoc_id, 'Bourses étudiantes 2026', '📚',
             'Attribution de 15 bourses d''études à des jeunes méritants issus de milieux modestes pour l''année 2026-2027.',
             10000, 0, 'LIVE', '2026-04-15', '2026-09-30',
             '0x' || repeat('4', 64),
             '2026-04-10 11:00:00+01', '2026-04-10 11:00:00+01'),

            (v_camp[5],  v_assoc_id, 'Plantation d''arbres', '🌳',
             '5 000 arbres plantés dans les zones péri-urbaines pour lutter contre le réchauffement climatique local.',
             3000, 0, 'ENDED', '2026-03-01', '2026-04-30',
             '0x' || repeat('5', 64),
             '2026-02-15 10:00:00+01', '2026-05-01 08:00:00+01'),

            (v_camp[6],  v_assoc_id, 'Matériel médical pour l''Afrique', '❤️',
             'Collecte et envoi de matériel médical réformé vers des structures de santé au Sénégal et au Mali.',
             8000, 0, 'ENDED', '2026-02-01', '2026-05-15',
             '0x' || repeat('6', 64),
             '2026-01-25 10:00:00+01', '2026-05-16 08:00:00+01'),

            (v_camp[7],  v_assoc_id, 'Soutien aux réfugiés', '🤝',
             'Hébergement d''urgence, cours de langue et accompagnement administratif pour les nouveaux arrivants.',
             20000, 0, 'LIVE', '2026-05-01', NULL,
             '0x' || repeat('7', 64),
             '2026-04-25 09:00:00+01', '2026-04-25 09:00:00+01'),

            (v_camp[8],  v_assoc_id, 'Festival culturel solidaire', '🎭',
             'Organisation d''un festival gratuit mêlant arts vivants, musique et ateliers citoyens ouverts à tous.',
             2000, 0, 'ENDED', '2026-03-20', '2026-04-20',
             '0x' || repeat('8', 64),
             '2026-03-15 10:00:00+01', '2026-04-21 09:00:00+01'),

            (v_camp[9],  v_assoc_id, 'Maison de retraite - travaux', '👴',
             'Réhabilitation complète de l''aile nord de la résidence seniors : isolation, fenêtres et sanitaires.',
             50000, 0, 'DRAFT', NULL, NULL,
             NULL,
             '2026-06-10 10:00:00+01', '2026-06-10 10:00:00+01'),

            (v_camp[10], v_assoc_id, 'Jardins partagés urbains', '🌱',
             'Création de 8 parcelles de jardins partagés dans des immeubles de quartiers prioritaires.',
             4000, 0, 'DRAFT', NULL, NULL,
             NULL,
             '2026-06-12 10:00:00+01', '2026-06-12 10:00:00+01'),

            (v_camp[11], v_assoc_id, 'Aide aux familles monoparentales', '💙',
             'Garde d''enfants, soutien scolaire et ateliers emploi pour les parents isolés en situation précaire.',
             12000, 0, 'LIVE', '2026-05-15', NULL,
             '0x' || repeat('b', 64),
             '2026-05-10 09:00:00+01', '2026-05-10 09:00:00+01'),

            (v_camp[12], v_assoc_id, 'Formation professionnelle', '🎓',
             'Financement de 30 formations certifiantes pour demandeurs d''emploi longue durée dans le numérique.',
             7500, 0, 'LIVE', '2026-06-01', NULL,
             '0x' || repeat('c', 64),
             '2026-05-28 10:00:00+01', '2026-05-28 10:00:00+01');

        -- ════════════════════════════════════════════════════════════
        -- 3. CAMPAIGN MILESTONES  (3 per non-draft campaign)
        --    Slot order in v_ms_frac: 25 % / 50 % / 100 % of goal.
        --    ENDED → all REACHED; LIVE → first REACHED, second CURRENT, rest LOCKED.
        -- ════════════════════════════════════════════════════════════
        FOREACH slot IN ARRAY v_active_slots LOOP
                SELECT goal, status INTO v_goal, v_status FROM campaigns WHERE id = v_camp[slot];

                FOR k IN 1..3 LOOP
                        IF v_status = 'ENDED' THEN
                            v_ms_status  := 'REACHED';
                            v_ms_reached := '2026-04-15 12:00:00+02'::TIMESTAMPTZ + (k * INTERVAL '3 days');
                        ELSE  -- LIVE
                            IF k = 1 THEN
                                v_ms_status := 'REACHED';  v_ms_reached := '2026-05-20 12:00:00+02';
                            ELSIF k = 2 THEN
                                v_ms_status := 'CURRENT';  v_ms_reached := NULL;
                            ELSE
                                v_ms_status := 'LOCKED';   v_ms_reached := NULL;
                            END IF;
                        END IF;

                        INSERT INTO campaign_milestones
                        (campaign_id, emoji, title, description, target_amount, status, sort_order, reached_at)
                        VALUES (
                                   v_camp[slot], v_ms_emoji[k], v_ms_title[k],
                                   'Palier ' || k || ' de la campagne.',
                                   round(v_goal * v_ms_frac[k], 2),
                                   v_ms_status, k - 1, v_ms_reached
                               );
                    END LOOP;
            END LOOP;

        -- ════════════════════════════════════════════════════════════
        -- 3b. BUDGETS  (sections EXPENSE/REVENUE + lignes) pour chaque
        --     campagne non-draft. Total EXPENSE == goal ; total REVENUE == goal.
        --     Les lignes EXPENSE servent de base aux payouts (section 7).
        -- ════════════════════════════════════════════════════════════
        FOREACH slot IN ARRAY v_active_slots LOOP
                SELECT goal INTO v_goal FROM campaigns WHERE id = v_camp[slot];

                -- Section CHARGES (EXPENSE)
                v_sec_id := gen_random_uuid();
                INSERT INTO campaign_budget_sections (id, campaign_id, side, code, name, sort_order)
                VALUES (v_sec_id, v_camp[slot], 'EXPENSE', 'EXPENSE', 'Charges prévisionnelles', 0);

                FOR k IN 1..4 LOOP
                        v_item_id := gen_random_uuid();
                        -- dernière ligne = reliquat pour garantir somme exacte == goal
                        IF k < 4 THEN
                            rand_amount := round(v_goal * v_exp_frac[k], 2);
                        ELSE
                            rand_amount := v_goal - round(v_goal * v_exp_frac[1], 2)
                                - round(v_goal * v_exp_frac[2], 2)
                                - round(v_goal * v_exp_frac[3], 2);
                        END IF;
                        INSERT INTO campaign_budget_items (id, section_id, label, amount, sort_order)
                        VALUES (v_item_id, v_sec_id, v_exp_label[k], rand_amount, k - 1);

                        v_exp_item_ids[(slot-1)*4 + k] := v_item_id;
                        v_exp_item_amt[(slot-1)*4 + k] := rand_amount;
                    END LOOP;

                -- Section PRODUITS (REVENUE)
                v_sec_id := gen_random_uuid();
                INSERT INTO campaign_budget_sections (id, campaign_id, side, code, name, sort_order)
                VALUES (v_sec_id, v_camp[slot], 'REVENUE', 'REVENUE', 'Produits prévisionnels', 1);

                FOR k IN 1..2 LOOP
                        IF k < 2 THEN
                            rand_amount := round(v_goal * v_rev_frac[k], 2);
                        ELSE
                            rand_amount := v_goal - round(v_goal * v_rev_frac[1], 2);
                        END IF;
                        INSERT INTO campaign_budget_items (section_id, label, amount, sort_order)
                        VALUES (v_sec_id, v_rev_label[k], rand_amount, k - 1);
                    END LOOP;
            END LOOP;
        FOR i IN 1..5 LOOP
                v_payee_ids[i]      := gen_random_uuid();
                v_payee_iban_ids[i] := gen_random_uuid();

                INSERT INTO payees
                (id, association_id, name, payee_type, identifier_1, identifier_2, activity_code, category, city, postal_code, active, created_at, updated_at)
                VALUES (
                           v_payee_ids[i], v_assoc_id, v_payee_names[i],
                           'COMPANY', v_payee_id1[i], v_payee_id1[i] || '00012',
                           '8899Z', 'Fournisseur', 'Paris', '75011', TRUE,
                           '2026-02-01 09:00:00+01', '2026-02-01 09:00:00+01'
                       );

                INSERT INTO payee_ibans
                (id, payee_id, iban, status, vop_result, vop_suggested_name, verified_at, created_at)
                VALUES (
                           v_payee_iban_ids[i], v_payee_ids[i], v_payee_iban[i],
                           v_payee_iban_status[i], v_payee_vop[i],
                           CASE WHEN v_payee_iban_status[i] = 'CLOSE_MATCH' THEN v_payee_names[i] || ' SAS' ELSE NULL END,
                           CASE WHEN v_payee_iban_status[i] = 'VERIFIED' THEN '2026-02-02 10:00:00+01'::TIMESTAMPTZ ELSE NULL END,
                           '2026-02-01 09:05:00+01'
                       );
            END LOOP;

        -- ════════════════════════════════════════════════════════════
        -- 4b. PERSON PAYEES  (payee_type = 'PERSON', pas de SIRET/SIREN)
        -- ════════════════════════════════════════════════════════════
        FOR i IN 1..3 LOOP
                v_person_ids[i]      := gen_random_uuid();
                v_person_iban_ids[i] := gen_random_uuid();

                INSERT INTO payees
                (id, association_id, name, payee_type, identifier_1, identifier_2,
                 activity_code, category, city, postal_code, active, created_at, updated_at)
                VALUES (
                           v_person_ids[i], v_assoc_id, v_person_names[i],
                           'PERSON', NULL, NULL,
                           NULL, 'Intervenant', 'Paris', '75010', TRUE,
                           '2026-03-01 09:00:00+01', '2026-03-01 09:00:00+01'
                       );

                INSERT INTO payee_ibans
                (id, payee_id, iban, status, vop_result, vop_suggested_name, verified_at, created_at)
                VALUES (
                           v_person_iban_ids[i], v_person_ids[i], v_person_iban[i],
                           'VERIFIED', 'MATCH', NULL,
                           '2026-03-02 10:00:00+01'::TIMESTAMPTZ,
                           '2026-03-01 09:05:00+01'
                       );
            END LOOP;

        -- ════════════════════════════════════════════════════════════
        -- 4aa. PAYEE WITH NO PAYOUTS — tests the delete path in the UI
        --      Not included in any payout generation loop.
        -- ════════════════════════════════════════════════════════════
        INSERT INTO payees
        (id, association_id, name, payee_type, identifier_1, identifier_2,
         activity_code, category, city, postal_code, active, created_at, updated_at)
        VALUES (
            'b0000000-0000-0000-0000-000000000001', v_assoc_id,
            'Graphique & Co SARL', 'COMPANY', '899900001', '89990000100012',
            '7420Z', 'Fournisseur', 'Lyon', '69002', TRUE,
            '2026-05-01 09:00:00+01', '2026-05-01 09:00:00+01'
        );
        INSERT INTO payee_ibans
        (id, payee_id, iban, status, vop_result, vop_suggested_name, verified_at, created_at)
        VALUES (
            'b0000000-0000-0000-0000-000000000002',
            'b0000000-0000-0000-0000-000000000001',
            'FR7610096000101234567890182',
            'VERIFIED', 'MATCH', NULL,
            '2026-05-02 10:00:00+01'::TIMESTAMPTZ,
            '2026-05-01 09:05:00+01'
        );

        -- ════════════════════════════════════════════════════════════
        -- 4c. ADDITIONAL IBANs on selected payees — all VERIFIED + MATCH
        --     Gives some payees multiple IBANs so the multi-IBAN UI path
        --     is exercisable in dev without extra setup.
        -- ════════════════════════════════════════════════════════════

        -- Payee 1 (Imprimerie Solidaire SARL) — 2nd IBAN, Société Générale
        INSERT INTO payee_ibans
        (id, payee_id, iban, status, vop_result, vop_suggested_name, verified_at, created_at)
        VALUES (
            gen_random_uuid(), v_payee_ids[1],
            'FR7630003035301234568790150',
            'VERIFIED', 'MATCH', NULL,
            '2026-02-16 10:00:00+01'::TIMESTAMPTZ,
            '2026-02-10 09:10:00+01'
        );

        -- Payee 2 (Banque Alimentaire IDF) — 2nd IBAN, La Banque Postale
        INSERT INTO payee_ibans
        (id, payee_id, iban, status, vop_result, vop_suggested_name, verified_at, created_at)
        VALUES (
            gen_random_uuid(), v_payee_ids[2],
            'FR7620041010051234568890163',
            'VERIFIED', 'MATCH', NULL,
            '2026-02-16 11:00:00+01'::TIMESTAMPTZ,
            '2026-02-11 09:10:00+01'
        );

        -- Payee 4 (Reboise & Cie) — 2nd IBAN, BNP (primary is already VERIFIED)
        INSERT INTO payee_ibans
        (id, payee_id, iban, status, vop_result, vop_suggested_name, verified_at, created_at)
        VALUES (
            gen_random_uuid(), v_payee_ids[4],
            'FR7630006000011234569890189',
            'VERIFIED', 'MATCH', NULL,
            '2026-02-17 10:00:00+01'::TIMESTAMPTZ,
            '2026-02-12 09:10:00+01'
        );

        -- Person 1 (Marie Leclerc) — 2nd IBAN, Crédit Agricole
        INSERT INTO payee_ibans
        (id, payee_id, iban, status, vop_result, vop_suggested_name, verified_at, created_at)
        VALUES (
            gen_random_uuid(), v_person_ids[1],
            'FR7618106006001234568790175',
            'VERIFIED', 'MATCH', NULL,
            '2026-03-03 10:00:00+01'::TIMESTAMPTZ,
            '2026-03-02 09:10:00+01'
        );

        -- Person 2 (Jean-Paul Moreau) — 2nd IBAN, LCL
        INSERT INTO payee_ibans
        (id, payee_id, iban, status, vop_result, vop_suggested_name, verified_at, created_at)
        VALUES (
            gen_random_uuid(), v_person_ids[2],
            'FR7614508590001234568990142',
            'VERIFIED', 'MATCH', NULL,
            '2026-03-03 11:00:00+01'::TIMESTAMPTZ,
            '2026-03-02 09:15:00+01'
        );

        -- ════════════════════════════════════════════════════════════
        -- 5. DONORS  (50 users + donor_profiles)
        -- ════════════════════════════════════════════════════════════
        FOR i IN 1..50 LOOP
                v_uid := gen_random_uuid();
                v_did := gen_random_uuid();
                v_donor_user_ids[i] := v_uid;
                v_donor_ids[i]      := v_did;

                v_email := CASE
                               WHEN v_donor_anon[i]
                                   THEN 'anon' || i || '@seed.local'
                               ELSE regexp_replace(
                                            translate(
                                                    lower(v_donor_names[i]),
                                                    'àâäéèêëîïôùûüç', 'aaaeeeeiioouuuc'
                                            ),
                                            '[^a-z0-9]+', '.', 'g'
                                    ) || '@example.com'
                    END;

                INSERT INTO users (id, email, role, provider, password_hash, display_name, email_verified, created_at, updated_at)
                VALUES (
                           v_uid, v_email, 'DONOR', 'EMAIL', HASH_DONOR,
                           v_donor_names[i], TRUE,
                           '2026-01-01'::TIMESTAMPTZ + (i * INTERVAL '2 days'),
                           '2026-01-01'::TIMESTAMPTZ + (i * INTERVAL '2 days')
                       );

                INSERT INTO donor_profiles (id, user_id, display_name, anonymous)
                VALUES (v_did, v_uid, v_donor_names[i], v_donor_anon[i]);
            END LOOP;

        -- ════════════════════════════════════════════════════════════
        -- 6. DONATIONS
        --    Each donor: 3–25 donations across the 10 non-draft campaigns.
        --    Window: 2026-03-17 → 2026-06-17 (92 days). 90 % confirmed.
        -- ════════════════════════════════════════════════════════════
        FOR i IN 1..50 LOOP
                n_donations := 3 + floor(random() * 23)::INT;  -- [3, 25]

                FOR j IN 1..n_donations LOOP
                        rand_camp := v_active_slots[1 + floor(random() * array_length(v_active_slots, 1))::INT];
                        rand_amount := (5 + floor(random() * 496))::NUMERIC(12,2);  -- [5, 500]

                        don_date := '2026-03-17 00:00:00+01'::TIMESTAMPTZ
                            + (floor(random() * 92) || ' days')::INTERVAL
                            + (floor(random() * 86400) || ' seconds')::INTERVAL;

                        confirmed := CASE WHEN random() < 0.9
                                              THEN don_date + INTERVAL '2 minutes'
                                          ELSE NULL
                            END;

                        pref_id := 'seed_' || substr(gen_random_uuid()::TEXT, 1, 12);

                        INSERT INTO donations (donor_id, campaign_id, amount, provider_ref, confirmed_at, created_at)
                        VALUES (v_donor_ids[i], v_camp[rand_camp], rand_amount, pref_id, confirmed, don_date);
                    END LOOP;
            END LOOP;

        -- ════════════════════════════════════════════════════════════
        -- 7. SYNC campaigns.raised FROM confirmed donations
        --    (fait AVANT les payouts pour pouvoir plafonner les décaissements)
        -- ════════════════════════════════════════════════════════════
        UPDATE campaigns c
        SET
            raised     = (SELECT COALESCE(SUM(d.amount), 0)
                          FROM donations d
                          WHERE d.campaign_id = c.id AND d.confirmed_at IS NOT NULL),
            updated_at = NOW();

        -- ════════════════════════════════════════════════════════════
        -- 8. PAYOUTS (+ backing onchain_jobs) — COHÉRENTS avec budget & dons
        --    Chaque payout correspond à une LIGNE DE BUDGET EXPENSE réelle de
        --    la campagne (même label, kind, type_code dérivé).
        --    Règle de cohérence : pour une campagne donnée, la somme des
        --    payouts ne dépasse JAMAIS min(raised confirmé, total charges) ;
        --    et le décaissement d'une ligne ne dépasse pas son montant budgété.
        --      • ENDED → décaisse ~70-95 % de chaque ligne (campagne soldée).
        --      • LIVE  → décaisse ~20-50 % (campagne en cours).
        -- ════════════════════════════════════════════════════════════
        FOREACH slot IN ARRAY v_payout_slots LOOP
                SELECT status, raised INTO v_status, v_goal FROM campaigns WHERE id = v_camp[slot];
                -- v_goal réutilisé ici pour "raised" (plafond global de décaissement)
                DECLARE
                    v_spent      NUMERIC(12,2) := 0;          -- cumul déjà décaissé
                    v_cap        NUMERIC(12,2);               -- plafond global
                    v_line_amt   NUMERIC(12,2);
                    v_ratio      NUMERIC;
                BEGIN
                    -- plafond = ce qui est réellement entré (raised confirmé)
                    v_cap := v_goal;

                    -- on parcourt les 4 lignes EXPENSE de la campagne
                    FOR k IN 1..4 LOOP
                            -- ratio de décaissement de la ligne selon le statut
                            IF v_status = 'ENDED' THEN
                                v_ratio := 0.70 + random() * 0.25;    -- 70-95 %
                            ELSE
                                v_ratio := 0.20 + random() * 0.30;    -- 20-50 %
                            END IF;

                            v_line_amt := round(v_exp_item_amt[(slot-1)*4 + k] * v_ratio, 2);

                            -- ne pas dépasser le plafond global restant
                            IF v_spent + v_line_amt > v_cap THEN
                                v_line_amt := v_cap - v_spent;
                            END IF;
                            EXIT WHEN v_line_amt <= 0;              -- plus de budget disponible

                            v_spent := v_spent + v_line_amt;

                            -- payee dont le type colle à la ligne : la ligne STAFF_COMP
                            -- (rémunération) va au payee "intervenants", sinon round-robin.
                            v_pidx := 1 + ((slot + k) % 5);

                            -- statut on-chain : ENDED toujours soldé ; LIVE ~85 % confirmé
                            IF v_status = 'ENDED' OR random() < 0.85 THEN
                                v_payout_status := 'CONFIRMED';
                            ELSE
                                v_payout_status := 'PENDING';
                            END IF;

                            don_date := '2026-04-01 00:00:00+02'::TIMESTAMPTZ
                                + (floor(random() * 70) || ' days')::INTERVAL
                                + (floor(random() * 86400) || ' seconds')::INTERVAL;
                            v_payout_conf := CASE WHEN v_payout_status = 'CONFIRMED'
                                                      THEN don_date + INTERVAL '6 hours' ELSE NULL END;

                            -- Backing on-chain job
                            v_payout_id := gen_random_uuid();
                            v_job_id    := gen_random_uuid();
                            INSERT INTO onchain_jobs
                            (id, action, payload_json, status, attempts, tx_hash, block_number, correlation_key, created_at, updated_at)
                            VALUES (
                                       v_job_id, 'RECORD_PAYOUT',
                                       jsonb_build_object(
                                               'payoutId',   v_payout_id,
                                               'campaignId', v_camp[slot],
                                               'amountCents', (v_line_amt * 100)::BIGINT
                                       ),
                                       CASE WHEN v_payout_status = 'CONFIRMED' THEN 'DONE' ELSE 'PENDING' END,
                                       CASE WHEN v_payout_status = 'CONFIRMED' THEN 1 ELSE 0 END,
                                       CASE WHEN v_payout_status = 'CONFIRMED'
                                                THEN '0x' || substr(md5(v_job_id::TEXT), 1, 64) ELSE NULL END,
                                       CASE WHEN v_payout_status = 'CONFIRMED'
                                                THEN (18000000 + floor(random() * 500000))::BIGINT ELSE NULL END,
                                       'payout:' || v_job_id::TEXT,
                                       don_date, COALESCE(v_payout_conf, don_date)
                                   );

                            INSERT INTO payouts
                            (id, campaign_id, payee_id, payee_iban_id, amount, kind, type_code, label,
                             status, created_at, confirmed_at, onchain_job_id)
                            VALUES (
                                       v_payout_id, v_camp[slot],
                                       v_payee_ids[v_pidx], v_payee_iban_ids[v_pidx],
                                       v_line_amt, v_exp_kind[k], v_exp_code[k], v_exp_label[k],
                                       v_payout_status, don_date, v_payout_conf, v_job_id
                                   );
                        END LOOP;
                END;
            END LOOP;

        -- ════════════════════════════════════════════════════════════
        -- 8b. PERSON PAYOUTS  (REMUNERATION — indemnités d'intervenants)
        --     4 campagnes × 3 intervenants = 12 payouts CONFIRMED
        -- ════════════════════════════════════════════════════════════
        FOREACH slot IN ARRAY v_person_slots LOOP
                FOR i IN 1..3 LOOP
                        v_payout_id := gen_random_uuid();
                        v_job_id    := gen_random_uuid();
                        don_date := '2026-05-01 00:00:00+02'::TIMESTAMPTZ
                            + (floor(random() * 45) || ' days')::INTERVAL
                            + (floor(random() * 86400) || ' seconds')::INTERVAL;
                        v_payout_conf := don_date + INTERVAL '4 hours';

                        INSERT INTO onchain_jobs
                        (id, action, payload_json, status, attempts, tx_hash, block_number, correlation_key, created_at, updated_at)
                        VALUES (
                                   v_job_id, 'RECORD_PAYOUT',
                                   jsonb_build_object(
                                           'payoutId',    v_payout_id,
                                           'campaignId',  v_camp[slot],
                                           'amountCents', (v_person_amounts[i] * 100)::BIGINT
                                   ),
                                   'DONE', 1,
                                   '0x' || substr(md5(v_job_id::TEXT), 1, 64),
                                   (18100000 + floor(random() * 500000))::BIGINT,
                                   'payout:p:' || v_job_id::TEXT,
                                   don_date, v_payout_conf
                               );

                        INSERT INTO payouts
                        (id, campaign_id, payee_id, payee_iban_id, amount, kind, type_code, label,
                         status, created_at, confirmed_at, onchain_job_id)
                        VALUES (
                                   v_payout_id, v_camp[slot],
                                   v_person_ids[i], v_person_iban_ids[i],
                                   v_person_amounts[i], 'REMUNERATION', 'STAFF_COMP',
                                   'Indemnités d''intervention — ' || v_person_names[i],
                                   'CONFIRMED', don_date, v_payout_conf, v_job_id
                               );
                    END LOOP;
            END LOOP;

        -- ════════════════════════════════════════════════════════════
        -- 9. CHAMPS V29 — category + reason par campagne
        -- ════════════════════════════════════════════════════════════
        UPDATE campaigns SET category = 'EDUCATION',    reason = 'Garantir l''accès au matériel scolaire pour les enfants défavorisés.'          WHERE id = v_camp[1];
        UPDATE campaigns SET category = 'HUMANITARIAN', reason = 'Répondre à l''urgence alimentaire des familles en grande précarité.'            WHERE id = v_camp[2];
        UPDATE campaigns SET category = 'SOCIAL',       reason = 'Mettre aux normes PMR et rénover l''espace communautaire de proximité.'         WHERE id = v_camp[3];
        UPDATE campaigns SET category = 'EDUCATION',    reason = 'Soutenir les jeunes méritants issus de milieux modestes.'                      WHERE id = v_camp[4];
        UPDATE campaigns SET category = 'ENVIRONMENT',  reason = 'Lutter contre le réchauffement climatique par la reforestation péri-urbaine.'   WHERE id = v_camp[5];
        UPDATE campaigns SET category = 'HUMANITARIAN', reason = 'Acheminer du matériel médical réformé vers des structures de santé en Afrique.' WHERE id = v_camp[6];
        UPDATE campaigns SET category = 'SOCIAL',       reason = 'Faciliter l''intégration des réfugiés par l''hébergement et l''accompagnement.' WHERE id = v_camp[7];
        UPDATE campaigns SET category = 'CULTURE',      reason = 'Organiser un événement gratuit fédérateur mêlant arts, musique et citoyenneté.' WHERE id = v_camp[8];
        UPDATE campaigns SET category = 'SOCIAL',       reason = 'Réhabiliter l''aile nord de la résidence seniors pour le confort des résidents.' WHERE id = v_camp[9];
        UPDATE campaigns SET category = 'ENVIRONMENT',  reason = 'Créer des espaces verts partagés dans les quartiers prioritaires.'              WHERE id = v_camp[10];
        UPDATE campaigns SET category = 'SOCIAL',       reason = 'Alléger le quotidien des parents isolés par la garde et le soutien scolaire.'   WHERE id = v_camp[11];
        UPDATE campaigns SET category = 'EDUCATION',    reason = 'Financer des formations certifiantes pour les demandeurs d''emploi longue durée.' WHERE id = v_camp[12];

        -- ════════════════════════════════════════════════════════════
        -- 10. CHAMP V30 — transparency_commitment sur les paliers atteints
        -- ════════════════════════════════════════════════════════════
        UPDATE campaign_milestones
        SET    transparency_commitment = 'Rapport financier détaillé publié dans l''espace transparent sous 30 jours.'
        WHERE  status = 'REACHED';

    END $$;
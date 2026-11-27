-- Every HelloAsso key check by an association (HelloAssoCredentialsService.verify), successful or not, so curators
-- can see why links fail (statistics screen). Append-only: rows are never updated nor deleted by the application.
--
-- No secret, no token: the client_id is stored masked (it is not a secret anyway). The organization fields are what
-- HelloAsso returned at that moment (null when the check stopped before reading the organization).
CREATE TABLE helloasso_link_attempts (
    id                   UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    association_id       UUID         NOT NULL REFERENCES association_profiles (id) ON DELETE CASCADE,
    attempted_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    outcome              VARCHAR(32)  NOT NULL,
    client_id_masked     VARCHAR(32),
    organizations_count  INTEGER,
    organization_slug    VARCHAR(255),
    organization_name    VARCHAR(255),
    -- HelloAsso's OrganizationType, stored as returned (external list, no CHECK).
    organization_type    VARCHAR(64),
    organization_rna     VARCHAR(20),
    -- The association's RNA as known by CommonLink at that moment (identifier or registry scan), null when unknown.
    association_rna      VARCHAR(20)
);

-- Values must stay identical to the HelloAssoLinkAttemptOutcome Kotlin enum entries.
ALTER TABLE helloasso_link_attempts
    ADD CONSTRAINT chk_helloasso_link_attempt_outcome
        CHECK (outcome IN ('MATCHED', 'NAME_MISMATCH', 'RNA_MISMATCH', 'ORGANIZATION_ALREADY_LINKED', 'INVALID_KEYS',
                           'ORGANIZATION_UNRESOLVED', 'HELLOASSO_UNAVAILABLE', 'HELLOASSO_RATE_LIMITED'));

-- Statistics read a period (attempted_at range).
CREATE INDEX idx_helloasso_link_attempts_attempted_at ON helloasso_link_attempts (attempted_at);
-- ON DELETE CASCADE and per-association reads.
CREATE INDEX idx_helloasso_link_attempts_association ON helloasso_link_attempts (association_id, attempted_at);

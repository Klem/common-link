-- Evidence of a manual release of HelloAsso donations by a curator (CEO decisions Q4 / Q5): when HelloAsso cannot tell
-- that a payout arrived (incident, link disconnected or broken), a curator makes the donations available on the
-- association's bank statement line — copied as text AND as a file of that single line, never the whole statement.
-- Stored in the database like the association documents (no external storage). Retention not defined yet (CEO
-- question Q8): no purge in place. The compliance journal references the evidence by id, never its content.
CREATE TABLE helloasso_funds_release_evidence (
    id              UUID          PRIMARY KEY,
    association_id  UUID          NOT NULL REFERENCES association_profiles (id) ON DELETE CASCADE,
    reason          TEXT          NOT NULL,
    statement_line  TEXT          NOT NULL,
    file_name       VARCHAR(255)  NOT NULL,
    content_type    VARCHAR(100)  NOT NULL,
    content         BYTEA         NOT NULL,
    created_by      UUID          NOT NULL REFERENCES users (id),
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- Per-association-per-year receipt sequence counter.
-- Atomically incremented via INSERT ... ON CONFLICT DO UPDATE RETURNING in ReceiptNumberService.
CREATE TABLE receipt_seq (
    association_id UUID     NOT NULL REFERENCES association_profiles (id) ON DELETE CASCADE,
    year           SMALLINT NOT NULL,
    last_seq       INTEGER  NOT NULL DEFAULT 0,
    CONSTRAINT pk_receipt_seq PRIMARY KEY (association_id, year)
);

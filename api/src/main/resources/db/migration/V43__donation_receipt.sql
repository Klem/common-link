-- Stores the generated Cerfa receipt PDF alongside each confirmed donation.
-- pdfBytes holds the exact bytes whose keccak256 is written on-chain.
-- emailed_at is null until the receipt is sent; guards against duplicate delivery.
CREATE TABLE donation_receipts (
    id             UUID        NOT NULL DEFAULT gen_random_uuid(),
    donation_id    UUID        NOT NULL,
    receipt_number VARCHAR(20) NOT NULL,
    pdf_bytes      BYTEA       NOT NULL,
    generated_at   TIMESTAMPTZ NOT NULL,
    emailed_at     TIMESTAMPTZ,
    CONSTRAINT pk_donation_receipts        PRIMARY KEY (id),
    CONSTRAINT uq_donation_receipts_don    UNIQUE (donation_id),
    CONSTRAINT fk_donation_receipts_don    FOREIGN KEY (donation_id)
        REFERENCES donations (id) ON DELETE CASCADE
);

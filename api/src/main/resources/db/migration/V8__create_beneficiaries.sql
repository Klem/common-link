CREATE TABLE beneficiaries
(
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    association_id UUID        NOT NULL REFERENCES association_profiles(id) ON DELETE CASCADE,
    name          VARCHAR(255) NOT NULL,
    identifier_1  VARCHAR(9)   NOT NULL,
    identifier_2  VARCHAR(14),
    activity_code VARCHAR(10),
    category      VARCHAR(100),
    city          VARCHAR(255),
    postal_code   VARCHAR(10),
    active        BOOLEAN      NOT NULL DEFAULT true,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT beneficiaries_asso_identifier1_unique UNIQUE (association_id, identifier_1)
);

CREATE INDEX beneficiaries_association_idx ON beneficiaries (association_id);

CREATE TABLE beneficiary_ibans
(
    id                 UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    beneficiary_id     UUID        NOT NULL REFERENCES beneficiaries(id) ON DELETE CASCADE,
    iban               VARCHAR(34) NOT NULL,
    status             VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    vop_result         VARCHAR(30),
    vop_suggested_name VARCHAR(255),
    vop_raw_response   TEXT,
    verified_at        TIMESTAMPTZ,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT beneficiary_ibans_unique UNIQUE (beneficiary_id, iban)
);

CREATE INDEX beneficiary_ibans_beneficiary_idx ON beneficiary_ibans (beneficiary_id);

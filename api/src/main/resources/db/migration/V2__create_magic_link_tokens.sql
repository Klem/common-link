CREATE TABLE magic_link_tokens
(
    id         uuid        NOT NULL DEFAULT gen_random_uuid(),
    email      varchar(255) NOT NULL,
    token_hash varchar(64) NOT NULL, -- SHA-256 hex du token opaque
    role       varchar(20) NOT NULL,
    expires_at timestamptz NOT NULL,
    used_at    timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT magic_link_tokens_pkey PRIMARY KEY (id),
    CONSTRAINT magic_link_tokens_token_hash_unique UNIQUE (token_hash)
);

CREATE INDEX magic_link_tokens_token_hash_idx ON magic_link_tokens (token_hash);
CREATE INDEX magic_link_tokens_email_used_at_idx ON magic_link_tokens (email, used_at);

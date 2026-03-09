CREATE TABLE refresh_tokens
(
    id         uuid        NOT NULL DEFAULT gen_random_uuid(),
    user_id    uuid        NOT NULL,
    token_hash varchar(64) NOT NULL, -- SHA-256 hex du refresh token opaque
    expires_at timestamptz NOT NULL,
    revoked    boolean     NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT refresh_tokens_pkey PRIMARY KEY (id),
    CONSTRAINT refresh_tokens_token_hash_unique UNIQUE (token_hash),
    CONSTRAINT refresh_tokens_user_id_fkey FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX refresh_tokens_token_hash_idx ON refresh_tokens (token_hash);
CREATE INDEX refresh_tokens_user_id_idx ON refresh_tokens (user_id);

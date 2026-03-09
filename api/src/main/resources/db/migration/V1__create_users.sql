CREATE TABLE users
(
    id             uuid         NOT NULL DEFAULT gen_random_uuid(),
    email          varchar(255) NOT NULL,
    role           varchar(20)  NOT NULL,
    provider       varchar(20)  NOT NULL,
    password_hash  text,
    google_sub     varchar(255),
    display_name   varchar(255),
    avatar_url     text,
    email_verified boolean      NOT NULL DEFAULT false,
    created_at     timestamptz  NOT NULL DEFAULT now(),
    updated_at     timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT users_pkey PRIMARY KEY (id),
    CONSTRAINT users_email_unique UNIQUE (email),
    CONSTRAINT users_role_check CHECK (role IN ('DONOR', 'ASSOCIATION')),
    CONSTRAINT users_provider_check CHECK (provider IN ('EMAIL', 'GOOGLE', 'MAGIC_LINK'))
);

CREATE INDEX users_email_idx ON users (email);

CREATE TABLE association_profiles
(
    id           uuid         NOT NULL DEFAULT gen_random_uuid(),
    user_id      uuid         NOT NULL,
    name         varchar(255) NOT NULL,
    identifier   varchar(9)   NOT NULL,
    city         varchar(255),
    postal_code  varchar(10),
    contact_name varchar(255),
    description  text,
    verified     boolean      NOT NULL DEFAULT false,

    CONSTRAINT association_profiles_pkey PRIMARY KEY (id),
    CONSTRAINT association_profiles_user_id_unique UNIQUE (user_id),
    CONSTRAINT association_profiles_user_id_fkey FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX association_profiles_siren_idx ON association_profiles (identifier);


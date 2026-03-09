CREATE TABLE donor_profiles
(
    id           uuid        NOT NULL DEFAULT gen_random_uuid(),
    user_id      uuid        NOT NULL,
    display_name varchar(255),
    anonymous    boolean     NOT NULL DEFAULT false,

    CONSTRAINT donor_profiles_pkey PRIMARY KEY (id),
    CONSTRAINT donor_profiles_user_id_unique UNIQUE (user_id),
    CONSTRAINT donor_profiles_user_id_fkey FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

-- "Forgot password" reuses the magic-link mechanism (AuthService.sendMagicLink /
-- verifyMagicLink) instead of a parallel reset-token system. A token issued for this purpose is
-- marked `password_reset`; once verified, it opens a short, single-use grace window
-- (`password_reset_grace_until`) during which setPassword may skip the currentPassword check that
-- normally guards a password replacement (security audit 2026-08-20, M7).
ALTER TABLE magic_link_tokens
    ADD COLUMN password_reset             BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN password_reset_grace_until TIMESTAMPTZ,
    ADD COLUMN password_reset_consumed_at TIMESTAMPTZ;

-- Native user accounts (Phase 6: auth ported off Supabase).
-- A self-contained users table replaces Supabase's auth.users so the service can
-- issue and verify its own JWTs. user_id in `timestamps` stays a bare UUID
-- referencing this table (no FK, mirroring the original's loose coupling).

CREATE TABLE public.app_users (
    id            UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    email         TEXT         NOT NULL,
    password_hash TEXT         NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Emails are stored lower-cased; uniqueness enforces one account per address.
CREATE UNIQUE INDEX uq_app_users_email ON public.app_users (email);

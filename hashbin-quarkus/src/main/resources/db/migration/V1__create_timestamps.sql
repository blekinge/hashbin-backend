-- Hashbin timestamps table.
-- Reflects the FINAL schema of the original Supabase project after all
-- seven migrations: hash renamed to hash_sha256, plus hash_md5/sha1/sha512.
-- RLS policies and the timestamps_public view are NOT reproduced here; the
-- Quarkus service uses its own DB role and enforces public/private column
-- projection at the DTO layer instead.

CREATE TABLE public.timestamps (
    id                UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    hash_sha256       TEXT         NOT NULL,
    hash_md5          TEXT,
    hash_sha1         TEXT,
    hash_sha512       TEXT,
    file_size         BIGINT       NOT NULL,
    file_name         TEXT,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    server_signature  TEXT         NOT NULL,
    user_id           UUID
);

-- Fast hash lookups (GET /api/verify).
CREATE INDEX idx_timestamps_hash_sha256 ON public.timestamps (hash_sha256);
CREATE INDEX idx_timestamps_hash_md5    ON public.timestamps (hash_md5);
CREATE INDEX idx_timestamps_hash_sha1   ON public.timestamps (hash_sha1);
CREATE INDEX idx_timestamps_hash_sha512 ON public.timestamps (hash_sha512);

-- "My timestamps" queries filter by user.
CREATE INDEX idx_timestamps_user_id ON public.timestamps (user_id);

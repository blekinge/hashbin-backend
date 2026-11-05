package net.hashbin.service;

import java.util.Optional;
import java.util.UUID;

/**
 * Resolves the authenticated user (if any) from an HTTP {@code Authorization}
 * header. Kept behind an interface so the JWT validation strategy (Supabase
 * round-trip here) is swappable and mockable in tests.
 */
public interface AuthService {

    /**
     * @param authorization raw {@code Authorization} header value, may be null
     * @return the user id when the bearer token is valid, otherwise empty
     */
    Optional<UUID> resolveUserId(String authorization);
}

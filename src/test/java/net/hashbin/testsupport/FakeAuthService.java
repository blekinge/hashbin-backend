package net.hashbin.testsupport;

import java.util.Optional;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

import io.quarkus.test.Mock;
import net.hashbin.service.AuthService;

/**
 * Test double for {@link AuthService} so endpoint tests don't call real Supabase.
 * Active only in the test profile ({@code @Mock} auto-selects it as an enabled
 * alternative, overriding {@code SupabaseAuthService}).
 *
 * <p>Tokens {@code "token-a"} / {@code "token-b"} map to fixed user ids; anything
 * else (or no bearer token) resolves to anonymous.
 */
@Mock
@Alternative
@ApplicationScoped
public class FakeAuthService implements AuthService {

    public static final UUID USER_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    public static final UUID USER_B = UUID.fromString("22222222-2222-2222-2222-222222222222");
    public static final String TOKEN_A = "token-a";
    public static final String TOKEN_B = "token-b";

    @Override
    public Optional<UUID> resolveUserId(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return Optional.empty();
        }
        String token = authorization.substring("Bearer ".length()).trim();
        if (TOKEN_A.equals(token)) {
            return Optional.of(USER_A);
        }
        if (TOKEN_B.equals(token)) {
            return Optional.of(USER_B);
        }
        return Optional.empty();
    }
}

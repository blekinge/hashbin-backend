package net.hashbin.rest.dto;

import java.util.UUID;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Response for successful register/login: the bearer token to use on
 * {@code /api/stamp} (for attribution) and {@code /api/my-timestamps}.
 *
 * <p>{@link RegisterForReflection} is required: the resource returns it inside a
 * JAX-RS {@code Response}; see {@link TimestampView} for the native-image rationale.
 */
@RegisterForReflection
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class AuthResponse {

    public String token;
    public String tokenType = "Bearer";
    public long expiresIn;
    public String userId;
    public String email;

    public static AuthResponse of(String token, UUID userId, String email, long expiresIn) {
        AuthResponse r = new AuthResponse();
        r.token = token;
        r.expiresIn = expiresIn;
        r.userId = userId == null ? null : userId.toString();
        r.email = email;
        return r;
    }
}

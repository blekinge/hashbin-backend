package net.hashbin.rest.dto;

import java.util.UUID;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * Response for successful register/login: the bearer token to use on
 * {@code /api/stamp} (for attribution) and {@code /api/my-timestamps}.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class AuthResponse {

    public String token;
    public String tokenType = "Bearer";
    public long expiresIn;
    public UUID userId;
    public String email;

    public static AuthResponse of(String token, UUID userId, String email, long expiresIn) {
        AuthResponse r = new AuthResponse();
        r.token = token;
        r.expiresIn = expiresIn;
        r.userId = userId;
        r.email = email;
        return r;
    }
}

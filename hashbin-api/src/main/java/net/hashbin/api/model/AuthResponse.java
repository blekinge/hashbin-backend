package net.hashbin.api.model;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/** Response for {@code POST /api/auth/register} and {@code POST /api/auth/login}. */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class AuthResponse {

    public String token;
    public String tokenType;
    public long expiresIn;
    public String userId;
    public String email;
}

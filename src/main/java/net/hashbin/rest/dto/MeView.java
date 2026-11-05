package net.hashbin.rest.dto;

import java.util.UUID;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/** Response for {@code GET /api/auth/me}. */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class MeView {
    public UUID userId;
    public String email;

    public static MeView of(UUID userId, String email) {
        MeView m = new MeView();
        m.userId = userId;
        m.email = email;
        return m;
    }
}

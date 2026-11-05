package net.hashbin.api.model;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/** Response for {@code GET /api/auth/me}. */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class MeView {

    public String userId;
    public String email;
}

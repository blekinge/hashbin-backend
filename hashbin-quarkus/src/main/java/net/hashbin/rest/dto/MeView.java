package net.hashbin.rest.dto;

import java.util.UUID;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Response for {@code GET /api/auth/me}.
 *
 * <p>{@link RegisterForReflection} is required: the resource returns it inside a
 * JAX-RS {@code Response}; see {@link TimestampView} for the native-image rationale.
 */
@RegisterForReflection
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class MeView {
    public String userId;
    public String email;

    public static MeView of(UUID userId, String email) {
        MeView m = new MeView();
        m.userId = userId == null ? null : userId.toString();
        m.email = email;
        return m;
    }
}

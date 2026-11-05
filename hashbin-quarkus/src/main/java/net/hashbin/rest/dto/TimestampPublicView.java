package net.hashbin.rest.dto;

import java.util.UUID;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import io.quarkus.runtime.annotations.RegisterForReflection;
import net.hashbin.model.Timestamp;

/**
 * Reduced, anonymized projection returned by {@code GET /api/verify}. Excludes
 * {@code file_name} and {@code user_id}, matching the original service which
 * selects only public columns (and which the Supabase {@code timestamps_public}
 * view also hid). Replaces the DB view with application-level projection.
 *
 * <p>{@link RegisterForReflection} is required because the resource returns it inside
 * a JAX-RS {@code Response}; see {@link TimestampView} for the native-image rationale.
 */
@RegisterForReflection
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class TimestampPublicView {

    public UUID id;
    public String hashSha256;
    public String hashMd5;
    public String hashSha1;
    public String hashSha512;
    public long fileSize;
    public String createdAt;
    public String serverSignature;

    public static TimestampPublicView from(Timestamp t) {
        TimestampPublicView v = new TimestampPublicView();
        v.id = t.id;
        v.hashSha256 = t.hashSha256;
        v.hashMd5 = t.hashMd5;
        v.hashSha1 = t.hashSha1;
        v.hashSha512 = t.hashSha512;
        v.fileSize = t.fileSize;
        v.createdAt = TimestampView.format(t.createdAt.toInstant());
        v.serverSignature = t.serverSignature;
        return v;
    }
}

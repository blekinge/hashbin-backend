package net.hashbin.api.model;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * One timestamp record as returned by the service. Used for both the full
 * projection ({@code POST /api/stamp}, {@code GET /api/my-timestamps}) and the
 * reduced public projection ({@code GET /api/verify}, {@code GET /api/explore}):
 * the public projection simply omits {@code file_name} and {@code user_id}, which
 * deserialize to {@code null}.
 *
 * <p>{@code created_at} is the exact ISO-8601 string the server signed, so a
 * caller can recompute the signature via {@link net.hashbin.api.security.Signature}.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class TimestampRecord {

    public String id;
    public String hashSha256;
    public String hashMd5;
    public String hashSha1;
    public String hashSha512;
    public long fileSize;
    public String fileName;
    public String createdAt;
    public String serverSignature;
    public String userId;
}

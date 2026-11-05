package net.hashbin.rest.dto;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.UUID;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import net.hashbin.model.Timestamp;

/**
 * Full timestamp projection returned by {@code POST /api/stamp} and
 * {@code GET /api/my-timestamps} — every stored column, including {@code null}s,
 * matching the original service which returns the whole inserted row.
 *
 * <p>{@code created_at} is rendered as ISO-8601 UTC with millisecond precision
 * and a trailing {@code Z} (e.g. {@code 2026-11-05T08:17:11.717Z}) — identical to
 * the string that was signed, so clients can recompute the signature.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class TimestampView {

    /** Millisecond precision, always 3 fractional digits and a trailing {@code Z}. */
    public static final DateTimeFormatter ISO_MILLIS = DateTimeFormatter
            .ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
            .withLocale(Locale.ROOT)
            .withZone(ZoneOffset.UTC);

    /** Formats an instant the way JavaScript's {@code Date.toISOString()} does. */
    public static String format(java.time.Instant instant) {
        return ISO_MILLIS.format(instant.truncatedTo(ChronoUnit.MILLIS));
    }

    public UUID id;
    public String hashSha256;
    public String hashMd5;
    public String hashSha1;
    public String hashSha512;
    public long fileSize;
    public String fileName;
    public String createdAt;
    public String serverSignature;
    public UUID userId;

    public static TimestampView from(Timestamp t) {
        TimestampView v = new TimestampView();
        v.id = t.id;
        v.hashSha256 = t.hashSha256;
        v.hashMd5 = t.hashMd5;
        v.hashSha1 = t.hashSha1;
        v.hashSha512 = t.hashSha512;
        v.fileSize = t.fileSize;
        v.fileName = t.fileName;
        v.createdAt = format(t.createdAt.toInstant());
        v.serverSignature = t.serverSignature;
        v.userId = t.userId;
        return v;
    }
}

package net.hashbin.rest;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.regex.Pattern;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import net.hashbin.model.Timestamp;
import net.hashbin.rest.dto.TimestampView;
import net.hashbin.service.AuthService;
import net.hashbin.service.HmacSigner;
import net.hashbin.service.RateLimiter;
import net.hashbin.service.TimestampService;

/**
 * POST /api/stamp — validate a set of client-computed file digests, sign them
 * with the server HMAC secret, and store an immutable timestamp record.
 *
 * <p>Validation rules, ordering and error strings intentionally mirror the
 * original Hono edge function so existing clients behave identically.
 */
@Path("/stamp")
@Tag(name = "Timestamps")
public class StampResource {

    private static final Pattern MD5 = Pattern.compile("^[a-f0-9]{32}$");
    private static final Pattern SHA1 = Pattern.compile("^[a-f0-9]{40}$");
    private static final Pattern SHA256 = Pattern.compile("^[a-f0-9]{64}$");
    private static final Pattern SHA512 = Pattern.compile("^[a-f0-9]{128}$");
    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024 * 1024 * 1024; // 10 TiB
    private static final int MAX_FILE_NAME = 500;

    @Inject
    HmacSigner signer;

    @Inject
    RateLimiter rateLimiter;

    @Inject
    TimestampService service;

    @Inject
    ObjectMapper mapper;

    @Inject
    AuthService auth;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Create a signed timestamp",
            description = "Rate-limited to 10 requests/minute per IP. SHA-256 is required; MD5, SHA-1 and SHA-512 are optional.")
    @APIResponse(responseCode = "200", description = "Timestamp created")
    @APIResponse(responseCode = "400", description = "Validation error")
    @APIResponse(responseCode = "429", description = "Rate limited")
    public Response stamp(String rawBody,
            @HeaderParam("x-forwarded-for") String forwardedFor,
            @HeaderParam("cf-connecting-ip") String cfIp,
            @HeaderParam("Authorization") String authorization) {

        String ip = clientIp(forwardedFor, cfIp);
        if (rateLimiter.isLimited(ip)) {
            return Errors.tooManyRequests("Too many requests. Try again later.");
        }

        JsonNode body = parse(rawBody);
        if (body == null || !body.isObject()) {
            return Errors.badRequest("Invalid JSON body");
        }

        // Required: SHA-256 (lowercase hex, 64 chars).
        String sha256 = asText(body.get("hash_sha256"));
        if (sha256 == null || !SHA256.matcher(sha256).matches()) {
            return Errors.badRequest("Invalid SHA-256 hash");
        }

        // Optional digests: validated only when present; a bad value is rejected.
        String md5 = asText(body.get("hash_md5"));
        if (body.hasNonNull("hash_md5") && (md5 == null || !MD5.matcher(md5).matches())) {
            return Errors.badRequest("Invalid MD5 hash");
        }
        String sha1 = asText(body.get("hash_sha1"));
        if (body.hasNonNull("hash_sha1") && (sha1 == null || !SHA1.matcher(sha1).matches())) {
            return Errors.badRequest("Invalid SHA-1 hash");
        }
        String sha512 = asText(body.get("hash_sha512"));
        if (body.hasNonNull("hash_sha512") && (sha512 == null || !SHA512.matcher(sha512).matches())) {
            return Errors.badRequest("Invalid SHA-512 hash");
        }

        // Required: file_size — a finite, non-negative number up to 10 TiB.
        JsonNode sizeNode = body.get("file_size");
        if (sizeNode == null || !sizeNode.isNumber()) {
            return Errors.badRequest("Invalid file_size");
        }
        BigDecimal size = sizeNode.decimalValue();
        if (size.signum() < 0 || size.compareTo(BigDecimal.valueOf(MAX_FILE_SIZE)) > 0) {
            return Errors.badRequest("Invalid file_size");
        }
        long fileSize = size.longValue();

        // Optional: file_name — string up to 500 chars.
        String fileName = null;
        if (body.hasNonNull("file_name")) {
            fileName = asText(body.get("file_name"));
            if (fileName == null || fileName.length() > MAX_FILE_NAME) {
                return Errors.badRequest("Invalid file_name");
            }
        }

        // created_at is generated once and reused verbatim for signing, storage
        // and the response, so the signature is always self-consistent.
        String createdAt = TimestampView.format(Instant.now());

        String signature;
        try {
            signature = signer.sign(sha256, createdAt);
        } catch (IllegalStateException misconfigured) {
            return Errors.serverError("Server misconfigured");
        }

        Timestamp entity = new Timestamp();
        entity.id = UUID.randomUUID();
        entity.hashSha256 = sha256;
        entity.hashMd5 = md5;
        entity.hashSha1 = sha1;
        entity.hashSha512 = sha512;
        entity.fileSize = fileSize;
        entity.fileName = fileName;
        entity.createdAt = OffsetDateTime.parse(createdAt); // ends with 'Z'
        entity.serverSignature = signature;
        entity.userId = auth.resolveUserId(authorization).orElse(null); // anonymous when no/invalid token

        try {
            service.save(entity);
        } catch (RuntimeException dbError) {
            return Errors.serverError("Failed to create timestamp");
        }

        return Response.ok(TimestampView.from(entity)).build();
    }

    private JsonNode parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return mapper.readTree(raw);
        } catch (Exception invalid) {
            return null;
        }
    }

    private static String asText(JsonNode node) {
        return (node != null && node.isTextual()) ? node.textValue() : null;
    }

    private static String clientIp(String forwardedFor, String cfIp) {
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        if (cfIp != null && !cfIp.isBlank()) {
            return cfIp.trim();
        }
        return "unknown";
    }
}

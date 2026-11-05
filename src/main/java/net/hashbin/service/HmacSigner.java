package net.hashbin.service;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Produces the server signature stored with each timestamp.
 *
 * <p>Compatible with the original Supabase Edge Function: it computes
 * {@code HMAC-SHA256(secret, "{hash_sha256}:{created_at}")} and hex-encodes the
 * result in lowercase. Records created by the Deno/Hono service therefore still
 * verify against this implementation.
 */
@ApplicationScoped
public class HmacSigner {

    @ConfigProperty(name = "hashbin.hmac.secret")
    String secret;

    /**
     * Signs the canonical message for a timestamp record.
     *
     * @throws IllegalStateException if no secret is configured, mirroring the
     *         original "HMAC_SECRET not configured" server error.
     */
    public String sign(String hashSha256, String createdAtIso) {
        if (secret == null || secret.isEmpty()) {
            throw new IllegalStateException("HMAC_SECRET not configured");
        }
        return hmacSha256Hex(secret, hashSha256 + ":" + createdAtIso);
    }

    /** Pure HMAC-SHA256 over {@code secret}/{@code message}, lowercase hex. */
    public static String hmacSha256Hex(String secret, String message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] out = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));

            StringBuilder hex = new StringBuilder(out.length * 2);
            for (byte b : out) {
                hex.append(Character.forDigit((b >> 4) & 0xf, 16));
                hex.append(Character.forDigit(b & 0xf, 16));
            }
            return hex.toString();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC signing failed", e);
        }
    }
}

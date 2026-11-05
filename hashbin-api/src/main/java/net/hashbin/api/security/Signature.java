package net.hashbin.api.security;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import net.hashbin.api.model.TimestampRecord;

/**
 * The client side of hashbin.net's signature scheme, identical to the backend's
 * {@code HmacSigner}: {@code HMAC-SHA256(secret, "{hash_sha256}:{created_at}")},
 * lowercase hex.
 *
 * <p>The server signs each record and returns {@code server_signature}. A client
 * that holds the shared secret uses {@link #verify} to independently confirm the
 * record was produced by a legitimate server — the core trust property of the
 * service. This lives in the client library precisely because it is the part of
 * the contract an OpenAPI document cannot express.
 */
public final class Signature {

    private Signature() {
    }

    /** The canonical message the server signs for a timestamp record. */
    public static String canonicalMessage(String hashSha256, String createdAtIso) {
        return hashSha256 + ":" + createdAtIso;
    }

    public static String sign(String secret, String hashSha256, String createdAtIso) {
        return hmacSha256Hex(secret, canonicalMessage(hashSha256, createdAtIso));
    }

    /** Recomputes the signature for {@code record} and compares constant-time. */
    public static boolean verify(String secret, TimestampRecord record) {
        String expected = sign(secret, record.hashSha256, record.createdAt);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                record.serverSignature.getBytes(StandardCharsets.UTF_8));
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

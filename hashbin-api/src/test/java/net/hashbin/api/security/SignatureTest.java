package net.hashbin.api.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.hashbin.api.model.TimestampRecord;

/**
 * Pins the HMAC to an authoritative, independent source (RFC 4231 test case 2)
 * so the client and the backend's HmacSigner cannot silently diverge, and
 * exercises the verify path the CLI/library uses.
 */
class SignatureTest {

    private static final String SECRET = "dev-only-insecure-secret-change-me";

    @Test
    void hmacSha256MatchesRfc4231TestCase2() {
        assertEquals(
                "5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843",
                Signature.hmacSha256Hex("Jefe", "what do ya want for nothing?"));
    }

    @Test
    void canonicalMessageIsHashColonCreatedAt() {
        String sha = "a".repeat(64);
        String createdAt = "2026-11-05T08:17:11.717Z";
        assertEquals(Signature.hmacSha256Hex(SECRET, sha + ":" + createdAt),
                Signature.sign(SECRET, sha, createdAt));
    }

    @Test
    void verifyAcceptsSignatureTheServerWouldProduce() {
        TimestampRecord r = record(SECRET);
        assertTrue(Signature.verify(SECRET, r));
    }

    @Test
    void verifyRejectsTamperingAndWrongSecret() {
        TimestampRecord tampered = record(SECRET);
        tampered.createdAt = "2020-01-01T00:00:00.000Z";
        assertFalse(Signature.verify(SECRET, tampered));

        assertFalse(Signature.verify("other-secret", record(SECRET)));
    }

    private static TimestampRecord record(String signingSecret) {
        TimestampRecord r = new TimestampRecord();
        r.hashSha256 = "a".repeat(64);
        r.createdAt = "2026-11-05T08:17:11.717Z";
        r.serverSignature = Signature.sign(signingSecret, r.hashSha256, r.createdAt);
        return r;
    }
}

package net.hashbin.api.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.hashbin.api.model.TimestampView;

/**
 * Pins the HMAC to an authoritative, independent source (RFC 4231 test case 2) so
 * the client and the backend's HmacSigner cannot silently diverge, and exercises
 * the verify path over the <em>generated</em> model getters.
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
        assertTrue(Signature.verify(SECRET, record(SECRET)));
    }

    @Test
    void verifyRejectsTamperingAndWrongSecret() {
        TimestampView tampered = record(SECRET);
        tampered.setCreatedAt("2020-01-01T00:00:00.000Z");
        assertFalse(Signature.verify(SECRET, tampered));

        assertFalse(Signature.verify("other-secret", record(SECRET)));
    }

    private static TimestampView record(String signingSecret) {
        TimestampView r = new TimestampView();
        r.setHashSha256("a".repeat(64));
        r.setCreatedAt("2026-11-05T08:17:11.717Z");
        r.setServerSignature(Signature.sign(signingSecret, r.getHashSha256(), r.getCreatedAt()));
        return r;
    }
}

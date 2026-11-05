package net.hashbin.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Cross-checks the HMAC implementation against a well-known published vector so
 * the signatures remain byte-identical to the original Deno edge function.
 */
class HmacSignerTest {

    @Test
    void matchesKnownRfcVector() {
        // HMAC-SHA256("key", "The quick brown fox jumps over the lazy dog")
        String expected = "f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8";
        assertEquals(expected, HmacSigner.hmacSha256Hex("key", "The quick brown fox jumps over the lazy dog"));
    }

    @Test
    void producesLowercaseHexOfLength64() {
        String sig = HmacSigner.hmacSha256Hex("secret", "abc:2026-01-01T00:00:00.000Z");
        assertEquals(64, sig.length());
        assertEquals(sig.toLowerCase(), sig);
        assertEquals(sig, sig.replaceAll("[^a-f0-9]", "")); // only hex chars
    }
}

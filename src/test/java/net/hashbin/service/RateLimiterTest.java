package net.hashbin.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RateLimiterTest {

    @Test
    void allowsTenThenBlocksEleventhInSameWindow() {
        RateLimiter limiter = new RateLimiter();
        String ip = "1.2.3.4";

        // First request opens the window; requests 1..10 are allowed.
        for (int i = 1; i <= RateLimiter.LIMIT; i++) {
            assertFalse(limiter.isLimited(ip), "request #" + i + " should be allowed");
        }
        // The 11th within the same window is rejected.
        assertTrue(limiter.isLimited(ip), "11th request should be rate limited");
    }

    @Test
    void countsEachIpSeparately() {
        RateLimiter limiter = new RateLimiter();
        for (int i = 0; i < RateLimiter.LIMIT; i++) {
            limiter.isLimited("10.0.0.1");
        }
        assertTrue(limiter.isLimited("10.0.0.1"), "10.0.0.1 should be exhausted");
        assertFalse(limiter.isLimited("10.0.0.2"), "10.0.0.2 is a fresh bucket");
    }
}

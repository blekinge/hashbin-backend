package net.hashbin.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Fixed-window per-IP rate limiter for the stamp endpoint.
 *
 * <p>Mirrors the original edge-function behaviour: the first request from an IP
 * opens a 60-second window; more than 10 requests within that window are
 * rejected. In-memory and single-node by design (as in the original); move to a
 * shared store (e.g. Redis) if the service is ever run as multiple replicas.
 */
@ApplicationScoped
public class RateLimiter {

    static final int LIMIT = 10;
    static final long WINDOW_MILLIS = 60_000L;

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    private static final class Window {
        int count;
        final long resetAt;

        Window(int count, long resetAt) {
            this.count = count;
            this.resetAt = resetAt;
        }
    }

    /**
     * Records a request from {@code ip} and reports whether it should be limited.
     *
     * @return {@code true} if this request exceeds the limit and must be rejected
     */
    public boolean isLimited(String ip) {
        long now = System.currentTimeMillis();
        boolean[] over = { false };
        windows.compute(ip, (key, window) -> {
            if (window == null || now > window.resetAt) {
                return new Window(1, now + WINDOW_MILLIS);
            }
            window.count++;
            over[0] = window.count > LIMIT;
            return window;
        });
        return over[0];
    }
}

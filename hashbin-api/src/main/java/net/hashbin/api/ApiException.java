package net.hashbin.api;

/**
 * A failed API call: a non-2xx status, or a transport/parse error. Carries the
 * HTTP {@link #status()} (0 for transport failures) and the human {@code error}
 * message extracted from the body ({@code {"error": "..."}}) when present, so a
 * caller can surface the server's own message. Mirrors the API's error contract.
 */
public class ApiException extends RuntimeException {

    private final int status;

    public ApiException(int status, String message) {
        super(message);
        this.status = status;
    }

    /** HTTP status, or {@code 0} when the request never reached the server. */
    public int status() {
        return status;
    }
}

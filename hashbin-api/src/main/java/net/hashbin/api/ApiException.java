package net.hashbin.api;

/**
 * A failed API call from {@link HashbinClient}: a non-2xx status, or a
 * transport/parse error. Carries the HTTP {@link #status()} ({@code 0} when the
 * request never reached the server) and the server's own {@code error} message
 * extracted from {@code {"error": "..."}} when present.
 *
 * <p>Hand-written (not generated): the client is hand-written by design, and the
 * API's error shape is a contract detail the OpenAPI document does not model.
 */
public class ApiException extends RuntimeException {

    private final int status;

    public ApiException(int status, String message) {
        super(message);
        this.status = status;
    }

    /** HTTP status, or {@code 0} for a transport failure. */
    public int status() {
        return status;
    }
}

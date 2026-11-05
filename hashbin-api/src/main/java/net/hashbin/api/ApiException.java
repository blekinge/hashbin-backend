package net.hashbin.api;

/**
 * The checked exception declared by the generated {@code *Api} client interfaces
 * (e.g. {@code TimestampsApi}) for failed calls: a non-2xx status or a transport/parse
 * error. Carries the HTTP {@link #status()} and the server's {@code error} message from
 * {@code {"error": "..."}}.
 *
 * <p>Hand-written, not generated: the OpenAPI generator references {@code ApiException}
 * in every operation signature but does not emit the class when supporting files are off.
 * Register {@link HashbinErrorMapper} on the built client so non-2xx responses actually
 * throw it (with the parsed {@code error} message); without a mapper the MicroProfile
 * implementation surfaces non-2xx via {@code WebApplicationException} per the MP spec.
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

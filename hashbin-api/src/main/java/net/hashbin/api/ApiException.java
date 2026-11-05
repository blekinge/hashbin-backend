package net.hashbin.api;

/**
 * The checked exception declared by the generated {@code *Api} client interfaces
 * (e.g. {@code TimestampsApi}) for failed calls: a non-2xx status or a transport/parse
 * error. Carries the HTTP {@link #status()} and the server's {@code error} message from
 * {@code {"error": "..."}}.
 *
 * <p>Hand-written, not generated: the OpenAPI generator references {@code ApiException}
 * in every operation signature but does not emit the class when supporting files are
 * off. To have the generated client actually <em>throw</em> it on a non-2xx response,
 * register an {@code ExceptionMapper} (or a {@code ClientResponseExceptionMapper}) on
 * the built client; otherwise the MicroProfile implementation surfaces non-2xx via
 * {@code WebApplicationException} per the MP spec.
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

package net.hashbin.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.rest.client.ext.ResponseExceptionMapper;

/**
 * Turns a non-2xx response from the hashbin service into an {@link ApiException} that
 * carries the HTTP status and the server's {@code {"error": "..."}} message, so the
 * generated {@code *Api} operations throw the exception they declare.
 *
 * <p>Register it on a built client (the generated interfaces are not hand-edited, so use
 * the builder rather than {@code @RegisterProvider}):
 *
 * <pre>{@code
 * TimestampsApi api = RestClientBuilder.newBuilder()
 *         .base(URI.create("https://hashbin.net"))
 *         .register(new BearerClientHeadersFactory(token))
 *         .register(new HashbinErrorMapper())
 *         .build(TimestampsApi.class);
 * }</pre>
 *
 * <p>Registered at the lowest precedence so a consumer's own, more specific mappers win.
 */
public class HashbinErrorMapper implements ResponseExceptionMapper<ApiException> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public boolean handles(int status, MultivaluedMap<String, Object> headers) {
        return status >= 400;
    }

    @Override
    public int getPriority() {
        return Integer.MAX_VALUE; // catch-all fallback; specific mappers take precedence
    }

    @Override
    public ApiException toThrowable(Response response) {
        String body = null;
        try {
            body = response.readEntity(String.class);
        } catch (RuntimeException unreadable) {
            // Entity already consumed or not a String; fall back to the status alone.
        }
        return toApiException(response.getStatus(), body);
    }

    /** Builds the {@link ApiException} from a status + raw body (extracted for testing). */
    static ApiException toApiException(int status, String body) {
        if (body != null && !body.isBlank()) {
            try {
                JsonNode node = MAPPER.readTree(body);
                if (node != null && node.hasNonNull("error")) {
                    return new ApiException(status, node.get("error").asText());
                }
            } catch (Exception notJson) {
                // Non-JSON error body (e.g. a gateway's HTML 5xx): fall through to the raw body.
                return new ApiException(status, body.trim());
            }
        }
        return new ApiException(status, "HTTP " + status);
    }
}

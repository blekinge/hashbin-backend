package net.hashbin.api.auth;

import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;

import org.eclipse.microprofile.rest.client.ext.ClientHeadersFactory;

/**
 * Adds an {@code Authorization: Bearer <token>} header to every request made by a
 * generated MicroProfile REST Client (e.g. {@code TimestampsApi}).
 *
 * <p>This exists because SmallRye OpenAPI hard-filters {@code Authorization} out of
 * the OpenAPI parameters (alongside {@code ACCEPT} / {@code CONTENT-TYPE}), so the
 * generated interface methods have no token argument. The MicroProfile-idiomatic way
 * to supply it is a {@link ClientHeadersFactory}, registered per client instance:
 *
 * <pre>{@code
 * TimestampsApi api = RestClientBuilder.newBuilder()
 *         .base(URI.create("https://hashbin.net"))
 *         .register(new BearerClientHeadersFactory(token))
 *         .build(TimestampsApi.class);
 * }</pre>
 *
 * <p>For CDI/Quarkus injection, a no-token factory (or one reading the token from
 * ambient JWT context) can be registered with {@code @RegisterProvider}. A blank/null
 * token adds no header (anonymous calls: verify, explore, health, unattributed stamp).
 */
public class BearerClientHeadersFactory implements ClientHeadersFactory {

    private final String bearerToken;

    public BearerClientHeadersFactory(String bearerToken) {
        this.bearerToken = bearerToken;
    }

    @Override
    public MultivaluedMap<String, String> update(MultivaluedMap<String, String> incomingHeaders,
            MultivaluedMap<String, String> clientOutgoingHeaders) {
        MultivaluedMap<String, String> headers = new MultivaluedHashMap<>();
        if (bearerToken != null && !bearerToken.isBlank()) {
            headers.add("Authorization", "Bearer " + bearerToken);
        }
        return headers;
    }
}

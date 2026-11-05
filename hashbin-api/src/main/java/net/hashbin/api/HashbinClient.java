package net.hashbin.api;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import net.hashbin.api.model.AuthRequest;
import net.hashbin.api.model.AuthResponse;
import net.hashbin.api.model.MeView;
import net.hashbin.api.model.StampRequest;
import net.hashbin.api.model.TimestampPublicView;
import net.hashbin.api.model.TimestampView;

/**
 * Hand-written HTTP client for the hashbin REST API, sitting on top of the
 * <em>generated</em> model classes in {@code net.hashbin.api.model}. The models are
 * regenerated every build from the service's OpenAPI document (so they cannot
 * drift); this transport layer is intentionally hand-written because the
 * signature-verification scheme is not expressible in OpenAPI and because a small
 * JDK-{@link HttpClient} wrapper keeps the library dependency-free.
 *
 * <p>One instance is thread-safe and reuses connections. Build it per base URL:
 *
 * <pre>{@code
 * HashbinClient client = new HashbinClient("https://hashbin.net/api");
 * TimestampView ts = client.stamp(new StampRequest().hashSha256(sha).fileSize(size), token);
 * boolean authentic = Signature.verify(System.getenv("HASHBIN_HMAC_SECRET"), ts);
 * }</pre>
 */
public class HashbinClient {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final String apiBase;
    private final HttpClient http;

    public HashbinClient(String apiBase) {
        this.apiBase = apiBase.endsWith("/") ? apiBase.substring(0, apiBase.length() - 1) : apiBase;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    // ── Endpoints ───────────────────────────────────────────────────────────

    /** GET /health → {@code {"status":"ok"}}. Returns {@code true} when healthy. */
    public boolean health() {
        JsonNode node = exchange("GET", "/health", null, null, null);
        return node != null && "ok".equals(node.path("status").asText(null));
    }

    /** POST /stamp → the created record. Pass {@code null} token for an anonymous stamp. */
    public TimestampView stamp(StampRequest request, String token) {
        return convert(exchange("POST", "/stamp", null, request, token), TimestampView.class);
    }

    /** GET /verify?hash=…&algorithm=… → matching records (oldest first). */
    public List<TimestampPublicView> verify(String hash, String algorithm) {
        Map<String, String> query = new LinkedHashMap<>();
        query.put("hash", hash);
        if (algorithm != null && !algorithm.isBlank() && !"auto".equalsIgnoreCase(algorithm)) {
            query.put("algorithm", algorithm);
        }
        return publicRecords(exchange("GET", "/verify", query, null, null));
    }

    /** GET /my-timestamps → the caller's records (token required). */
    public List<TimestampView> myTimestamps(String token) {
        return fullRecords(exchange("GET", "/my-timestamps", null, null, token));
    }

    /** GET /explore?limit=…&offset=… → recent public records. */
    public List<TimestampPublicView> explore(Integer limit, Integer offset) {
        Map<String, String> query = new LinkedHashMap<>();
        if (limit != null) {
            query.put("limit", limit.toString());
        }
        if (offset != null) {
            query.put("offset", offset.toString());
        }
        return publicRecords(exchange("GET", "/explore", query, null, null));
    }

    /** POST /auth/register → token + profile. */
    public AuthResponse register(String email, String password) {
        return convert(exchange("POST", "/auth/register", null, credentials(email, password), null),
                AuthResponse.class);
    }

    /** POST /auth/login → token + profile. */
    public AuthResponse login(String email, String password) {
        return convert(exchange("POST", "/auth/login", null, credentials(email, password), null),
                AuthResponse.class);
    }

    /** GET /auth/me → profile (token required). */
    public MeView me(String token) {
        return convert(exchange("GET", "/auth/me", null, null, token), MeView.class);
    }

    // ── Plumbing ────────────────────────────────────────────────────────────

    private static AuthRequest credentials(String email, String password) {
        return new AuthRequest().email(email).password(password);
    }

    private JsonNode exchange(String method, String path, Map<String, String> query, Object body, String token) {
        URI uri = URI.create(apiBase + path + queryString(query));

        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json");
        if (token != null && !token.isBlank()) {
            builder.header("Authorization", "Bearer " + token);
        }
        if ("POST".equals(method)) {
            builder.POST(HttpRequest.BodyPublishers.ofString(writeJson(body)));
        } else {
            builder.GET();
        }

        HttpResponse<String> response;
        try {
            response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            String cause = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            throw new ApiException(0, "API unreachable (" + uri + "): " + cause);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(0, "request interrupted");
        }
        return handle(response);
    }

    private JsonNode handle(HttpResponse<String> response) {
        int status = response.statusCode();
        String raw = response.body();
        try {
            JsonNode node = MAPPER.readTree(raw);
            if (status < 200 || status >= 300) {
                String message = node != null && node.hasNonNull("error")
                        ? node.get("error").asText()
                        : "HTTP " + status;
                throw new ApiException(status, message);
            }
            return node;
        } catch (IOException notJson) {
            // A gateway may return non-JSON (e.g. HTML) on 5xx.
            throw new ApiException(status, raw == null || raw.isBlank() ? "HTTP " + status : raw.trim());
        }
    }

    private String writeJson(Object body) {
        try {
            return MAPPER.writeValueAsString(body == null ? Map.of() : body);
        } catch (IOException e) {
            throw new IllegalStateException("failed to serialize request body", e);
        }
    }

    private <T> T convert(JsonNode node, Class<T> type) {
        try {
            return MAPPER.treeToValue(node, type);
        } catch (IOException e) {
            throw new ApiException(0, "Unexpected response shape: " + e.getMessage());
        }
    }

    private List<TimestampView> fullRecords(JsonNode node) {
        return convertList(node, new TypeReference<List<TimestampView>>() {
        });
    }

    private List<TimestampPublicView> publicRecords(JsonNode node) {
        return convertList(node, new TypeReference<List<TimestampPublicView>>() {
        });
    }

    private <T> List<T> convertList(JsonNode node, TypeReference<List<T>> type) {
        try {
            return MAPPER.convertValue(node, type);
        } catch (IllegalArgumentException e) {
            throw new ApiException(0, "Unexpected response shape: " + e.getMessage());
        }
    }

    private static String queryString(Map<String, String> query) {
        if (query == null || query.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("?");
        query.forEach((k, v) -> sb.append(encode(k)).append('=').append(encode(v)).append('&'));
        sb.setLength(sb.length() - 1); // drop trailing &
        return sb.toString();
    }

    private static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}

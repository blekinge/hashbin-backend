package net.hashbin.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import net.hashbin.api.model.TimestampPublicView;
import net.hashbin.api.model.TimestampView;
import net.hashbin.api.model.StampRequest;
import net.hashbin.api.security.Signature;

/**
 * Drives the hand-written {@link HashbinClient} against a throwaway JDK server that
 * speaks the hashbin wire contract, using the <em>generated</em> models. Proves:
 * the generated {@code StampRequest} serializes to the snake_case body the server
 * expects, responses bind to the generated models, the {@code {"error":...}} →
 * {@link ApiException} mapping works, and a stub-signed record verifies with the
 * shared secret. No live backend / container required.
 */
class HashbinClientTest {

    private static final String SECRET = "dev-only-insecure-secret-change-me";
    private static final String CREATED_AT = "2026-11-05T08:17:11.717Z";
    private static final ObjectMapper M = new ObjectMapper();

    private HttpServer server;
    private HashbinClient client;

    @BeforeEach
    void startStub() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", freePort()), 0);
        server.createContext("/api/health", ex -> respond(ex, 200, "{\"status\":\"ok\"}"));
        server.createContext("/api/verify", this::verify);
        server.createContext("/api/stamp", this::stamp);
        server.createContext("/api/explore", ex -> respond(ex, 500, "{\"error\":\"database unavailable\"}"));
        server.start();
        client = new HashbinClient("http://127.0.0.1:" + server.getAddress().getPort() + "/api");
    }

    @AfterEach
    void stopStub() {
        server.stop(0);
    }

    private static int freePort() throws Exception {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }

    @Test
    void healthReturnsTrueWhenOk() {
        assertTrue(client.health());
    }

    @Test
    void verifyBindsPublicProjectionAndVerifiesSignature() {
        List<TimestampPublicView> found = client.verify("b".repeat(64), "auto");
        assertEquals(1, found.size());
        TimestampPublicView r = found.get(0);
        assertEquals("b".repeat(64), r.getHashSha256());
        assertEquals(123L, r.getFileSize());
        assertTrue(Signature.verify(SECRET, r), "stub signature should verify with the shared secret");
    }

    @Test
    void stampSendsSnakeCaseBodyAndParsesSignedResponse() {
        StampRequest req = new StampRequest()
                .hashSha256("f".repeat(64)).hashMd5("a".repeat(32)).fileSize(42L).fileName("x.txt");

        TimestampView created = client.stamp(req, null);

        assertEquals("f".repeat(64), created.getHashSha256());
        assertEquals("x.txt", created.getFileName());
        assertTrue(Signature.verify(SECRET, created));
    }

    @Test
    void non2xxStatusBecomesApiExceptionWithServerMessage() {
        ApiException ex = assertThrows(ApiException.class, () -> client.explore(3, 0));
        assertEquals(500, ex.status());
        assertEquals("database unavailable", ex.getMessage());
    }

    @Test
    void unreachableHostUsesStatusZero() {
        HashbinClient dead = new HashbinClient("http://127.0.0.1:1/api");
        ApiException ex = assertThrows(ApiException.class, dead::health);
        assertEquals(0, ex.status());
        assertFalse(ex.getMessage().isEmpty());
    }

    // ── stub handlers ───────────────────────────────────────────────────────

    private void verify(HttpExchange ex) throws java.io.IOException {
        String sha = "b".repeat(64);
        String json = "[{\"id\":\"11111111-1111-1111-1111-111111111111\",\"hash_sha256\":\"" + sha
                + "\",\"file_size\":123,\"created_at\":\"" + CREATED_AT
                + "\",\"server_signature\":\"" + Signature.sign(SECRET, sha, CREATED_AT) + "\"}]";
        respond(ex, 200, json);
    }

    private void stamp(HttpExchange ex) throws java.io.IOException {
        JsonNode req = M.readTree(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        // The stub echoes what the real server validates: the snake_case keys.
        String sha = req.path("hash_sha256").asText("");
        if (!sha.matches("^[a-f0-9]{64}$")) {
            respond(ex, 400, "{\"error\":\"Invalid SHA-256 hash\"}");
            return;
        }
        String fileName = req.hasNonNull("file_name") ? "\"" + req.path("file_name").asText() + "\"" : "null";
        String json = "{\"id\":\"22222222-2222-2222-2222-222222222222\",\"hash_sha256\":\"" + sha
                + "\",\"file_size\":" + req.path("file_size").asLong()
                + ",\"file_name\":" + fileName
                + ",\"created_at\":\"" + CREATED_AT
                + "\",\"server_signature\":\"" + Signature.sign(SECRET, sha, CREATED_AT) + "\"}";
        respond(ex, 200, json);
    }

    private void respond(HttpExchange ex, int status, String body) throws java.io.IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }
}

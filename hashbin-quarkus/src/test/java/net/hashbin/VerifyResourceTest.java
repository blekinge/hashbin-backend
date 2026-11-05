package net.hashbin;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;

@QuarkusTest
class VerifyResourceTest {

    private static final SecureRandom RNG = new SecureRandom();
    private static final AtomicInteger IP_SEQ = new AtomicInteger(100);

    private static String hex(int len) {
        byte[] b = new byte[len / 2];
        RNG.nextBytes(b);
        StringBuilder sb = new StringBuilder();
        for (byte x : b) {
            sb.append(Character.forDigit((x >> 4) & 0xf, 16));
            sb.append(Character.forDigit(x & 0xf, 16));
        }
        return sb.toString();
    }

    /** Stamps a row (anonymous, unique IP) and returns its unique sha256. */
    private String stampFresh(String sha256, String md5, String name) {
        String body = "{\"hash_sha256\":\"" + sha256 + "\",\"file_size\":10"
                + (md5 != null ? ",\"hash_md5\":\"" + md5 + "\"" : "")
                + (name != null ? ",\"file_name\":\"" + name + "\"" : "") + "}";
        given().header("X-Forwarded-For", "10.70." + IP_SEQ.incrementAndGet() + ".1")
                .contentType(ContentType.JSON).body(body)
                .when().post("/api/stamp").then().statusCode(200);
        return sha256;
    }

    private JsonPath verifyQuery(String hash, String algorithm) {
        var req = given().queryParam("hash", hash);
        if (algorithm != null) {
            req = req.queryParam("algorithm", algorithm);
        }
        return req.when().get("/api/verify").then().statusCode(200).extract().body().jsonPath();
    }

    @Test
    void autoDetectsSha256AndReturnsPublicProjection() {
        String sha = stampFresh(hex(64), null, "secret-name.txt");
        List<Map<String, Object>> rows = verifyQuery(sha, null).getList("");
        assertFalse(rows.isEmpty(), "the stamped hash should be found");
        Map<String, Object> match = rows.stream()
                .filter(r -> sha.equals(r.get("hash_sha256"))).findFirst().orElseThrow();
        assertTrue(match.containsKey("server_signature"));
        assertFalse(match.containsKey("user_id"), "public view must hide user_id");
        assertFalse(match.containsKey("file_name"), "public view must hide file_name");
    }

    @Test
    void autoDetectsMd5ByLength() {
        String md5 = hex(32);
        stampFresh(hex(64), md5, null);
        List<Map<String, Object>> rows = verifyQuery(md5, null).getList("");
        assertTrue(rows.stream().anyMatch(r -> md5.equals(r.get("hash_md5"))));
    }

    @Test
    void explicitAlgorithmParameter() {
        String sha = stampFresh(hex(64), null, null);
        List<Map<String, Object>> rows = verifyQuery(sha, "sha256").getList("");
        assertTrue(rows.stream().anyMatch(r -> sha.equals(r.get("hash_sha256"))));
    }

    @Test
    void unknownHashReturnsNoMatches() {
        String unused = hex(64); // freshly random; never stamped
        List<Map<String, Object>> rows = verifyQuery(unused, null).getList("");
        assertTrue(rows.stream().noneMatch(r -> unused.equals(r.get("hash_sha256"))),
                "a hash that was never stamped must not appear");
    }

    @Test
    void missingHashIsBadRequest() {
        given().when().get("/api/verify")
                .then().statusCode(400).body("error", equalTo("Missing hash parameter"));
    }

    @Test
    void invalidHashFormatInAutoMode() {
        given().queryParam("hash", "abc")
                .when().get("/api/verify")
                .then().statusCode(400).body("error", equalTo("Invalid hash format"));
    }

    @Test
    void unsupportedAlgorithm() {
        given().queryParam("hash", hex(64)).queryParam("algorithm", "crc32")
                .when().get("/api/verify")
                .then().statusCode(400).body("error", equalTo("Unsupported algorithm"));
    }
}

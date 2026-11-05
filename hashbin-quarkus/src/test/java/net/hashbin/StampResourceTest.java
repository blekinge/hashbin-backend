package net.hashbin;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import net.hashbin.model.Timestamp;
import net.hashbin.service.HmacSigner;

@QuarkusTest
class StampResourceTest {

    // Valid digests of the empty file, used as realistic test fixtures.
    private static final String SHA256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
    private static final String MD5 = "d41d8cd98f00b204e9800998ecf8427e";
    private static final String SHA1 = "da39a3ee5e6b4b0d3255bfef95601890afd80709";
    private static final String SHA512 = "cf83e1357eefb8bdf1542850d66d8007d620e4050b5715dc83f4a921d36ce9ce"
            + "47d0d13c5d85f2b0ff8318d2877eec2f63b931bd47417a81a538327af927da3e";

    // Unique IP per request so the shared in-memory rate limiter never leaks
    // state across test methods.
    private static final AtomicInteger IP_SEQ = new AtomicInteger();

    @ConfigProperty(name = "hashbin.hmac.secret")
    String secret;

    private String uniqueIp() {
        return "10.9." + (IP_SEQ.incrementAndGet() % 200) + "." + (IP_SEQ.get() % 250);
    }

    private io.restassured.specification.RequestSpecification stamp(String body) {
        return given().header("X-Forwarded-For", uniqueIp()).contentType(ContentType.JSON).body(body);
    }

    @Test
    void stampsValidSha256Only() {
        String body = "{\"hash_sha256\":\"" + SHA256 + "\",\"file_size\":1234}";
        stamp(body)
                .when().post("/api/stamp")
                .then()
                .statusCode(200)
                .body("id", notNullValue())
                .body("hash_sha256", equalTo(SHA256))
                .body("file_size", equalTo(1234))
                .body("created_at", notNullValue())
                .body("server_signature", notNullValue());
    }

    @Test
    void storesAllHashesAndName() {
        String body = "{"
                + "\"hash_sha256\":\"" + SHA256 + "\","
                + "\"hash_md5\":\"" + MD5 + "\","
                + "\"hash_sha1\":\"" + SHA1 + "\","
                + "\"hash_sha512\":\"" + SHA512 + "\","
                + "\"file_size\":99,"
                + "\"file_name\":\"report.pdf\""
                + "}";
        stamp(body)
                .when().post("/api/stamp")
                .then()
                .statusCode(200)
                .body("hash_md5", equalTo(MD5))
                .body("hash_sha1", equalTo(SHA1))
                .body("hash_sha512", equalTo(SHA512))
                .body("file_name", equalTo("report.pdf"));
    }

    @Test
    void signatureMatchesHmacOfHashAndCreatedAt() {
        String body = "{\"hash_sha256\":\"" + SHA256 + "\",\"file_size\":42}";
        var json = stamp(body)
                .when().post("/api/stamp")
                .then().statusCode(200).extract().body().jsonPath();

        String createdAt = json.getString("created_at");
        String returnedSig = json.getString("server_signature");
        String expected = HmacSigner.hmacSha256Hex(secret, SHA256 + ":" + createdAt);
        assertEquals(expected, returnedSig, "signature must equal HMAC-SHA256(secret, hash:created_at)");
    }

    @Test
    @Transactional
    void recordIsPersisted() {
        String body = "{\"hash_sha256\":\"" + SHA256 + "\",\"file_size\":7}";
        String id = stamp(body)
                .when().post("/api/stamp")
                .then().statusCode(200).extract().body().jsonPath().getString("id");

        Timestamp stored = Timestamp.<Timestamp>findById(UUID.fromString(id));
        assertNotNull(stored, "row should be committed to the database");
        assertEquals(SHA256, stored.hashSha256);
        assertEquals(7L, stored.fileSize);
    }

    @Test
    void rejectsMissingSha256() {
        stamp("{\"file_size\":1}")
                .when().post("/api/stamp")
                .then().statusCode(400).body("error", equalTo("Invalid SHA-256 hash"));
    }

    @Test
    void rejectsMalformedSha256() {
        stamp("{\"hash_sha256\":\"nothex\",\"file_size\":1}")
                .when().post("/api/stamp")
                .then().statusCode(400).body("error", equalTo("Invalid SHA-256 hash"));
    }

    @Test
    void rejectsInvalidMd5() {
        stamp("{\"hash_sha256\":\"" + SHA256 + "\",\"hash_md5\":\"xyz\",\"file_size\":1}")
                .when().post("/api/stamp")
                .then().statusCode(400).body("error", equalTo("Invalid MD5 hash"));
    }

    @Test
    void rejectsInvalidFileSizeMissing() {
        stamp("{\"hash_sha256\":\"" + SHA256 + "\"}")
                .when().post("/api/stamp")
                .then().statusCode(400).body("error", equalTo("Invalid file_size"));
    }

    @Test
    void rejectsNegativeFileSize() {
        stamp("{\"hash_sha256\":\"" + SHA256 + "\",\"file_size\":-5}")
                .when().post("/api/stamp")
                .then().statusCode(400).body("error", equalTo("Invalid file_size"));
    }

    @Test
    void rejectsTooLargeFileSize() {
        // 10 TiB + 1 byte exceeds the bound.
        stamp("{\"hash_sha256\":\"" + SHA256 + "\",\"file_size\":10995116277761}")
                .when().post("/api/stamp")
                .then().statusCode(400).body("error", equalTo("Invalid file_size"));
    }

    @Test
    void rejectsTooLongFileName() {
        String longName = "a".repeat(501);
        stamp("{\"hash_sha256\":\"" + SHA256 + "\",\"file_size\":1,\"file_name\":\"" + longName + "\"}")
                .when().post("/api/stamp")
                .then().statusCode(400).body("error", equalTo("Invalid file_name"));
    }

    @Test
    void rejectsInvalidJsonBody() {
        stamp("this is not json")
                .when().post("/api/stamp")
                .then().statusCode(400).body("error", equalTo("Invalid JSON body"));
    }

    @Test
    void rateLimitsEleventhRequestFromSameIp() {
        String ip = "203.0.113." + (IP_SEQ.incrementAndGet() % 250);
        String body = "{\"hash_sha256\":\"" + SHA256 + "\",\"file_size\":1}";
        for (int i = 1; i <= RateLimiterConstants.LIMIT; i++) {
            given().header("X-Forwarded-For", ip).contentType(ContentType.JSON).body(body)
                    .when().post("/api/stamp").then().statusCode(200);
        }
        given().header("X-Forwarded-For", ip).contentType(ContentType.JSON).body(body)
                .when().post("/api/stamp")
                .then().statusCode(429).body("error", equalTo("Too many requests. Try again later."));
    }

    /** Mirrors RateLimiter.LIMIT without depending on the package-private field. */
    private static final class RateLimiterConstants {
        static final int LIMIT = 10;
    }
}

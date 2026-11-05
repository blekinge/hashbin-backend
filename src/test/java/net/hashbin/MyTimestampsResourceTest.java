package net.hashbin;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import net.hashbin.testsupport.FakeAuthService;

@QuarkusTest
class MyTimestampsResourceTest {

    private static final SecureRandom RNG = new SecureRandom();
    private static final AtomicInteger IP_SEQ = new AtomicInteger(200);

    private static String hex64() {
        byte[] b = new byte[32];
        RNG.nextBytes(b);
        StringBuilder sb = new StringBuilder();
        for (byte x : b) {
            sb.append(Character.forDigit((x >> 4) & 0xf, 16));
            sb.append(Character.forDigit(x & 0xf, 16));
        }
        return sb.toString();
    }

    private void stamp(String sha256, String bearerToken) {
        var req = given().header("X-Forwarded-For", "10.80." + IP_SEQ.incrementAndGet() + ".1")
                .contentType(ContentType.JSON)
                .body("{\"hash_sha256\":\"" + sha256 + "\",\"file_size\":5}");
        if (bearerToken != null) {
            req = req.header("Authorization", "Bearer " + bearerToken);
        }
        req.when().post("/api/stamp").then().statusCode(200);
    }

    private Response myTimestamps(String bearerToken) {
        var req = given();
        if (bearerToken != null) {
            req = req.header("Authorization", "Bearer " + bearerToken);
        }
        return req.when().get("/api/my-timestamps");
    }

    @Test
    void returnsOnlyOwnTimestamps() {
        String hashA = hex64();
        String hashB = hex64();
        String hashAnon = hex64();
        stamp(hashA, FakeAuthService.TOKEN_A);
        stamp(hashB, FakeAuthService.TOKEN_B);
        stamp(hashAnon, null);

        List<Map<String, Object>> rows = myTimestamps(FakeAuthService.TOKEN_A)
                .then().statusCode(200).extract().body().jsonPath().getList("");

        assertTrue(rows.stream().anyMatch(r -> hashA.equals(r.get("hash_sha256"))),
                "user A should see their own stamp");
        assertTrue(rows.stream().noneMatch(r -> hashB.equals(r.get("hash_sha256"))),
                "user A must not see user B's stamp");
        assertTrue(rows.stream().noneMatch(r -> hashAnon.equals(r.get("hash_sha256"))),
                "user A must not see anonymous stamps");
    }

    @Test
    void fullProjectionIncludesFileNameAndUserId() {
        String hash = hex64();
        given().header("X-Forwarded-For", "10.80." + IP_SEQ.incrementAndGet() + ".1")
                .header("Authorization", "Bearer " + FakeAuthService.TOKEN_A)
                .contentType(ContentType.JSON)
                .body("{\"hash_sha256\":\"" + hash + "\",\"file_size\":5,\"file_name\":\"mine.pdf\"}")
                .when().post("/api/stamp").then().statusCode(200);

        List<Map<String, Object>> rows = myTimestamps(FakeAuthService.TOKEN_A)
                .then().statusCode(200).extract().body().jsonPath().getList("");
        Map<String, Object> match = rows.stream()
                .filter(r -> hash.equals(r.get("hash_sha256"))).findFirst().orElseThrow();
        assertTrue(match.containsKey("user_id"), "authenticated view includes user_id");
        assertTrue(match.containsKey("file_name"), "authenticated view includes file_name");
    }

    @Test
    void requiresAuthentication() {
        myTimestamps(null)
                .then().statusCode(401).body("error", equalTo("Authentication required"));
    }

    @Test
    void invalidTokenIsUnauthorized() {
        myTimestamps("not-a-real-token")
                .then().statusCode(401).body("error", equalTo("Authentication required"));
    }
}

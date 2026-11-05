package net.hashbin;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

@QuarkusTest
class AuthResourceTest {

    private static final SecureRandom RNG = new SecureRandom();
    private static final AtomicInteger SEQ = new AtomicInteger();

    private static String uniqueEmail() {
        return "user" + System.nanoTime() + SEQ.incrementAndGet() + "@example.com";
    }

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

    private String json(String email, String password) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }

    @Test
    void registerReturnsTokenThenMeReturnsProfile() {
        String email = uniqueEmail();
        String token = given().contentType(ContentType.JSON).body(json(email, "hunter2pass"))
                .when().post("/api/auth/register")
                .then().statusCode(201)
                .body("token", notNullValue())
                .body("token_type", equalTo("Bearer"))
                .body("expires_in", is(86400))
                .body("email", equalTo(email))
                .extract().body().jsonPath().getString("token");

        given().header("Authorization", "Bearer " + token)
                .when().get("/api/auth/me")
                .then().statusCode(200).body("email", equalTo(email));
    }

    @Test
    void registerNormalizesEmailAndLoginSucceeds() {
        String mixed = "MixedCase" + System.nanoTime() + "@Example.com";
        String lower = mixed.toLowerCase();
        given().contentType(ContentType.JSON).body(json(mixed, "hunter2pass"))
                .when().post("/api/auth/register")
                .then().statusCode(201).body("email", equalTo(lower));

        // Login using the original mixed-case address must still work.
        given().contentType(ContentType.JSON).body(json(mixed, "hunter2pass"))
                .when().post("/api/auth/login")
                .then().statusCode(200).body("token", notNullValue()).body("email", equalTo(lower));
    }

    @Test
    void duplicateEmailRejected() {
        String email = uniqueEmail();
        given().contentType(ContentType.JSON).body(json(email, "hunter2pass"))
                .when().post("/api/auth/register").then().statusCode(201);
        given().contentType(ContentType.JSON).body(json(email, "different123"))
                .when().post("/api/auth/register")
                .then().statusCode(409).body("error", equalTo("Email already registered"));
    }

    @Test
    void invalidEmailRejected() {
        given().contentType(ContentType.JSON).body(json("not-an-email", "hunter2pass"))
                .when().post("/api/auth/register")
                .then().statusCode(400).body("error", equalTo("Invalid email"));
    }

    @Test
    void shortPasswordRejected() {
        given().contentType(ContentType.JSON).body(json(uniqueEmail(), "short"))
                .when().post("/api/auth/register")
                .then().statusCode(400)
                .body("error", equalTo("Password must be between 8 and 72 characters"));
    }

    @Test
    void loginWrongPasswordRejected() {
        String email = uniqueEmail();
        given().contentType(ContentType.JSON).body(json(email, "hunter2pass"))
                .when().post("/api/auth/register").then().statusCode(201);
        given().contentType(ContentType.JSON).body(json(email, "wrongpassword"))
                .when().post("/api/auth/login")
                .then().statusCode(401).body("error", equalTo("Invalid email or password"));
    }

    @Test
    void loginUnknownEmailRejected() {
        given().contentType(ContentType.JSON).body(json(uniqueEmail(), "hunter2pass"))
                .when().post("/api/auth/login")
                .then().statusCode(401).body("error", equalTo("Invalid email or password"));
    }

    @Test
    void meRequiresAuthentication() {
        given().when().get("/api/auth/me")
                .then().statusCode(401).body("error", equalTo("Authentication required"));
    }

    @Test
    void issuedTokenAttributsStampAndAppearsInMyTimestamps() {
        String email = uniqueEmail();
        String token = given().contentType(ContentType.JSON).body(json(email, "hunter2pass"))
                .when().post("/api/auth/register")
                .then().statusCode(201).extract().body().jsonPath().getString("token");

        String hash = hex64();
        given().header("X-Forwarded-For", "10.90." + (SEQ.get() % 200) + ".1")
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON).body("{\"hash_sha256\":\"" + hash + "\",\"file_size\":1}")
                .when().post("/api/stamp").then().statusCode(200)
                .body("user_id", not(nullValue()));

        List<Map<String, Object>> rows = given().header("Authorization", "Bearer " + token)
                .when().get("/api/my-timestamps")
                .then().statusCode(200).extract().body().jsonPath().getList("");

        assertTrue(rows.stream().anyMatch(r -> hash.equals(r.get("hash_sha256"))),
                "the registered user's token should attribute the stamp and list it");
    }
}

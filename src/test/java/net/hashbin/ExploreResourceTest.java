package net.hashbin;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;

@QuarkusTest
class ExploreResourceTest {

    private static final SecureRandom RNG = new SecureRandom();
    private static final AtomicInteger IP_SEQ = new AtomicInteger(300);
    /** The shared test DB accumulates rows across classes; guarantee we have
     *  enough to exercise paging at least once without depending on order. */
    private static final AtomicBoolean SEEDED = new AtomicBoolean(false);

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

    /** Stamps an anonymous row from a fresh IP and returns its unique sha256. */
    private String stampFresh(String name) {
        String sha = hex64();
        String body = "{\"hash_sha256\":\"" + sha + "\",\"file_size\":7"
                + (name != null ? ",\"file_name\":\"" + name + "\"" : "") + "}";
        given().header("X-Forwarded-For", "10.90." + IP_SEQ.incrementAndGet() + ".1")
                .contentType(ContentType.JSON).body(body)
                .when().post("/api/stamp").then().statusCode(200);
        return sha;
    }

    @BeforeEach
    void seedSomeRows() {
        if (SEEDED.compareAndSet(false, true)) {
            for (int i = 0; i < 6; i++) {
                stampFresh(null);
            }
        }
    }

    private JsonPath explore(String limit, String offset) {
        var req = given();
        if (limit != null) {
            req = req.queryParam("limit", limit);
        }
        if (offset != null) {
            req = req.queryParam("offset", offset);
        }
        return req.when().get("/api/explore").then().statusCode(200).extract().body().jsonPath();
    }

    private static List<String> ids(JsonPath page) {
        return page.getList("id", String.class);
    }

    @Test
    void returnsPublicProjectionAndHidesPrivateFields() {
        String sha = stampFresh("hidden-name.txt");
        List<Map<String, Object>> rows = explore("100", "0").getList("");
        Map<String, Object> match = rows.stream()
                .filter(r -> sha.equals(r.get("hash_sha256"))).findFirst().orElseThrow();
        assertTrue(match.containsKey("server_signature"), "public view keeps server_signature");
        assertFalse(match.containsKey("user_id"), "explore must hide user_id");
        assertFalse(match.containsKey("file_name"), "explore must hide file_name");
    }

    @Test
    void resultsAreOrderedNewestFirst() {
        List<Map<String, Object>> rows = explore("100", "0").getList("");
        // ISO-8601 UTC 'Z' strings sort lexicographically = chronologically.
        for (int i = 1; i < rows.size(); i++) {
            String prev = (String) rows.get(i - 1).get("created_at");
            String curr = (String) rows.get(i).get("created_at");
            assertTrue(prev.compareTo(curr) >= 0,
                    "page must be non-increasing in created_at, but " + prev + " precedes " + curr);
        }
    }

    @Test
    void honoursLimitParameter() {
        assertEquals(3, explore("3", "0").getList("").size(), "limit=3 returns exactly 3 rows");
    }

    @Test
    void defaultsToTwentyWhenLimitOmitted() {
        List<String> defaulted = ids(explore(null, null));
        assertTrue(defaulted.size() <= 20, "default page size is at most 20");
        List<String> explicit = ids(explore("20", null));
        assertEquals(explicit, defaulted, "omitted limit == explicit limit=20");
    }

    @Test
    void offsetPagesAreStableAndContiguous() {
        List<String> first2 = ids(explore("2", "0"));
        List<String> second2 = ids(explore("2", "2"));
        List<String> first4 = ids(explore("4", "0"));
        assertEquals(2, first2.size());
        assertEquals(2, second2.size());
        assertEquals(first4, List.of(first2.get(0), first2.get(1), second2.get(0), second2.get(1)),
                "offset paging must tile the un-paged order exactly");
    }

    @Test
    void rejectsOutOfRangeLimit() {
        String msg = "limit must be between 1 and 100";
        given().queryParam("limit", "0").when().get("/api/explore")
                .then().statusCode(400).body("error", equalTo(msg));
        given().queryParam("limit", "-1").when().get("/api/explore")
                .then().statusCode(400).body("error", equalTo(msg));
        given().queryParam("limit", "1000").when().get("/api/explore")
                .then().statusCode(400).body("error", equalTo(msg));
    }

    @Test
    void rejectsNonNumericLimit() {
        given().queryParam("limit", "abc").when().get("/api/explore")
                .then().statusCode(400).body("error", equalTo("Invalid limit parameter"));
    }

    @Test
    void rejectsNegativeOffset() {
        given().queryParam("offset", "-1").when().get("/api/explore")
                .then().statusCode(400).body("error", equalTo("offset must be zero or greater"));
    }

    @Test
    void rejectsNonNumericOffset() {
        given().queryParam("offset", "xyz").when().get("/api/explore")
                .then().statusCode(400).body("error", equalTo("Invalid offset parameter"));
    }
}

package net.hashbin;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class OpenApiSpecResourceTest {

    @SuppressWarnings("unchecked")
    @Test
    void servesGeneratedSpecAtAppPath() {
        Map<String, Object> doc = given()
                .when().get("/api/openapi.json")
                .then()
                .statusCode(200)
                .contentType(org.hamcrest.Matchers.containsString("application/json"))
                .extract().body().jsonPath().getMap("");

        assertNotNull(doc.get("openapi"), "document should declare an openapi version");
        Map<String, Object> paths = (Map<String, Object>) doc.get("paths");
        assertNotNull(paths, "document should list paths");
        for (String p : new String[] { "/api/stamp", "/api/verify", "/api/my-timestamps", "/api/health" }) {
            assertTrue(paths.containsKey(p), "spec should include " + p + " (found: " + paths.keySet() + ")");
        }
    }

    @Test
    void infoMatchesBuildConfig() {
        given().when().get("/api/openapi.json")
                .then().statusCode(200).body("info.title", equalTo("Hashbin API"));
    }
}

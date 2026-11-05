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
        for (String p : new String[] { "/api/stamp", "/api/verify", "/api/my-timestamps", "/api/explore", "/api/health" }) {
            assertTrue(paths.containsKey(p), "spec should include " + p + " (found: " + paths.keySet() + ")");
        }
    }

    @Test
    void infoMatchesBuildConfig() {
        given().when().get("/api/openapi.json")
                .then().statusCode(200).body("info.title", equalTo("Hashbin API"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void specCarriesTypedSchemas() {
        Map<String, Object> doc = given()
                .when().get("/api/openapi.json")
                .then().statusCode(200)
                .extract().body().jsonPath().getMap("");

        Map<String, Object> schemas = (Map<String, Object>) ((Map<String, Object>) doc.get("components")).get("schemas");
        for (String name : new String[] { "StampRequest", "TimestampView", "TimestampPublicView", "AuthResponse", "MeView" }) {
            org.junit.jupiter.api.Assertions.assertTrue(schemas.containsKey(name),
                    "components.schemas should define " + name + " (found: " + schemas.keySet() + ")");
        }

        // /stamp request body must reference the typed StampRequest (not a generic object).
        Map<String, Object> stampReq = (Map<String, Object>) givenPath(doc, "/api/stamp", "post")
                .get("requestBody");
        Map<String, Object> json = (Map<String, Object>) ((Map<String, Object>) stampReq.get("content")).get("application/json");
        org.junit.jupiter.api.Assertions.assertEquals("#/components/schemas/StampRequest",
                ((Map<String, Object>) json.get("schema")).get("$ref"));

        // StampRequest properties are snake_case on the wire.
        Map<String, Object> props = (Map<String, Object>) ((Map<String, Object>) schemas.get("StampRequest")).get("properties");
        org.junit.jupiter.api.Assertions.assertTrue(props.containsKey("hash_sha256") && props.containsKey("file_size"),
                "StampRequest should expose snake_case properties (found: " + props.keySet() + ")");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> givenPath(Map<String, Object> doc, String path, String method) {
        Map<String, Object> paths = (Map<String, Object>) doc.get("paths");
        return (Map<String, Object>) ((Map<String, Object>) paths.get(path)).get(method);
    }
}

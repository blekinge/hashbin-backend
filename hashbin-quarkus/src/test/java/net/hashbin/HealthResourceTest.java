package net.hashbin;

import static io.restassured.RestAssured.given;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class HealthResourceTest {

    @Test
    public void healthReturnsOk() {
        given()
                .when().get("/api/health")
                .then()
                .log()
                .ifError()
                .statusCode(200)
                .body("status", org.hamcrest.Matchers.is("ok"));
    }
}

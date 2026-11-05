package net.hashbin.rest;

import java.util.Map;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Builds error responses in the exact shape the original API returns:
 * {@code {"error": "<message>"}} with a specific HTTP status.
 */
final class Errors {

    private Errors() {
    }

    static Response badRequest(String message) {
        return json(400, message);
    }

    static Response tooManyRequests(String message) {
        return json(429, message);
    }

    static Response serverError(String message) {
        return json(500, message);
    }

    static Response json(int status, String message) {
        return Response.status(status)
                .type(MediaType.APPLICATION_JSON)
                .entity(Map.of("error", message))
                .build();
    }
}

package net.hashbin.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import jakarta.ws.rs.core.MultivaluedHashMap;

class HashbinErrorMapperTest {

    private final HashbinErrorMapper mapper = new HashbinErrorMapper();

    @Test
    void handlesOnlyClientAndServerErrors() {
        assertTrue(mapper.handles(400, new MultivaluedHashMap<>()));
        assertTrue(mapper.handles(500, new MultivaluedHashMap<>()));
        assertFalse(mapper.handles(200, new MultivaluedHashMap<>()));
        assertFalse(mapper.handles(302, new MultivaluedHashMap<>()));
    }

    @Test
    void extractsServerErrorJson() {
        ApiException ex = HashbinErrorMapper.toApiException(400, "{\"error\":\"Invalid SHA-256 hash\"}");
        assertEquals(400, ex.status());
        assertEquals("Invalid SHA-256 hash", ex.getMessage());
    }

    @Test
    void fallsBackToHttpStatusWhenNoErrorField() {
        assertEquals("HTTP 404", HashbinErrorMapper.toApiException(404, "{\"message\":\"gone\"}").getMessage());
        assertEquals("HTTP 500", HashbinErrorMapper.toApiException(500, null).getMessage());
        assertEquals("HTTP 503", HashbinErrorMapper.toApiException(503, "   ").getMessage());
    }

    @Test
    void keepsRawBodyWhenNotJson() {
        // A gateway may return HTML for a 5xx; surface it rather than losing it.
        ApiException ex = HashbinErrorMapper.toApiException(502, "<html>Bad Gateway</html>");
        assertEquals(502, ex.status());
        assertEquals("<html>Bad Gateway</html>", ex.getMessage());
    }

    @Test
    void returnsApiExceptionType() {
        assertInstanceOf(ApiException.class, HashbinErrorMapper.toApiException(400, "{}"));
    }
}

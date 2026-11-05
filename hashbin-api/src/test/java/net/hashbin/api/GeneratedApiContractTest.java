package net.hashbin.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MultivaluedHashMap;

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import net.hashbin.api.auth.BearerClientHeadersFactory;

/**
 * Guards the *generated* surface without needing a MicroProfile runtime:
 * asserts the {@code TimestampsApi} interface is a {@code @RegisterRestClient} with the
 * expected {@code @Path} operations, that the OpenAPI documents are bundled on the
 * classpath, and that {@link BearerClientHeadersFactory} injects the auth header.
 */
class GeneratedApiContractTest {

    @Test
    void timestampsApiIsAMicroprofileRestClient() {
        assertNotNull(TimestampsApi.class.getAnnotation(RegisterRestClient.class),
                "generated TimestampsApi must be @RegisterRestClient");
        Path classPath = TimestampsApi.class.getAnnotation(Path.class);
        assertNotNull(classPath);
        assertEquals("/api", classPath.value());

        Set<String> opPaths = new HashSet<>();
        for (Method m : TimestampsApi.class.getDeclaredMethods()) {
            boolean annotated = m.isAnnotationPresent(GET.class) || m.isAnnotationPresent(POST.class)
                    || m.isAnnotationPresent(DELETE.class);
            Path p = m.getAnnotation(Path.class);
            if (annotated && p != null) {
                opPaths.add(p.value());
            }
        }
        assertTrue(opPaths.containsAll(Set.of("/stamp", "/verify", "/explore", "/my-timestamps")),
                "expected stamp/verify/explore/my-timestamps, found: " + opPaths);
    }

    @Test
    void openapiDocumentsAreBundledOnTheClasspath() throws Exception {
        for (String resource : new String[] { "/META-INF/openapi/openapi.yaml", "/META-INF/openapi/openapi.json" }) {
            try (InputStream in = GeneratedApiContractTest.class.getResourceAsStream(resource)) {
                assertNotNull(in, "expected " + resource + " on the classpath");
                String body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                assertTrue(body.contains("openapi"), resource + " should be an OpenAPI document");
                assertTrue(body.contains("/api/stamp"), resource + " should describe /api/stamp");
            }
        }
    }

    @Test
    void bearerFactoryAddsAuthHeader() {
        var headers = new BearerClientHeadersFactory("tok123").update(new MultivaluedHashMap<>(), new MultivaluedHashMap<>());
        assertEquals("Bearer tok123", headers.getFirst("Authorization"));
    }

    @Test
    void bearerFactoryOmitsHeaderWhenNoToken() {
        var headers = new BearerClientHeadersFactory(null).update(new MultivaluedHashMap<>(), new MultivaluedHashMap<>());
        assertTrue(headers.isEmpty(), "no token => no Authorization header");
    }
}

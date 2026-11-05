package net.hashbin.rest;

import java.io.InputStream;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import io.quarkus.smallrye.openapi.runtime.OpenApiConstants;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Serves the auto-generated OpenAPI 3.1 document at {@code GET /api/openapi.json}.
 *
 * <p>The existing frontend (the React Docs page) fetches the spec from
 * {@code {API_BASE}/openapi.json}, while Quarkus exposes the generated doc at the
 * non-application path {@code /q/openapi}. This resource bridges the two by
 * returning the exact bytes Quarkus generated at build time — the same
 * {@code META-INF/quarkus-generated-openapi-doc.JSON} resource that
 * {@code OpenApiDocumentService} reads — so the spec never drifts from the
 * endpoint annotations and there is no hand-written copy to maintain.
 */
@Path("/openapi.json")
@Tag(name = "Docs")
public class OpenApiSpecResource {

    /** Resource key of the build-time generated JSON document. */
    private static final String GENERATED_JSON = OpenApiConstants.BASE_NAME + "JSON";

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "OpenAPI 3.1 specification",
            description = "The same auto-generated specification served at /q/openapi.")
    @APIResponse(responseCode = "200", description = "OpenAPI JSON document")
    public Response spec() {
        byte[] document = readGeneratedSpec();
        if (document == null) {
            return Errors.serverError("OpenAPI document is not available");
        }
        return Response.ok(document).type(MediaType.APPLICATION_JSON).build();
    }

    private static byte[] readGeneratedSpec() {
        // Prefer the classloader Quarkus registered the generated doc under (works
        // in dev, test and prod); fall back to the thread context classloader.
        ClassLoader classLoader = OpenApiConstants.classLoader != null
                ? OpenApiConstants.classLoader
                : Thread.currentThread().getContextClassLoader();
        try (InputStream in = classLoader.getResourceAsStream(GENERATED_JSON)) {
            return in == null ? null : in.readAllBytes();
        } catch (Exception failure) {
            return null;
        }
    }
}

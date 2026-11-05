package net.hashbin.rest;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import io.quarkus.smallrye.openapi.runtime.OpenApiConstants;
import io.quarkus.smallrye.openapi.runtime.OpenApiDocumentService;
import io.smallrye.openapi.runtime.io.Format;

import jakarta.inject.Inject;
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
 * non-application path {@code /q/openapi}. This resource bridges the two by returning
 * the same document via {@link OpenApiDocumentService#getDocument(String, Format)} —
 * the very service that backs {@code /q/openapi} — so the spec never drifts from the
 * endpoint annotations and there is no hand-written copy to maintain.
 *
 * <p>Note on implementation: an earlier version read the build-time generated classpath
 * resource {@code META-INF/quarkus-generated-openapi-doc.JSON} directly. That was an
 * internal detail (the resource is no longer emitted in the same way across Quarkus
 * versions and is absent under {@code @QuarkusTest}); going through the documented
 * {@code OpenApiDocumentService} is supported and works identically in dev, test and
 * prod.
 */
@Path("/openapi.json")
@Tag(name = "Docs")
public class OpenApiSpecResource {

    @Inject
    OpenApiDocumentService documentService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "OpenAPI 3.1 specification",
            description = "The same auto-generated specification served at /q/openapi.")
    @APIResponse(responseCode = "200", description = "OpenAPI JSON document")
    public Response spec() {
        byte[] document = documentService.getDocument(OpenApiConstants.DEFAULT_DOCUMENT_NAME, Format.JSON);
        if (document == null || document.length == 0) {
            return Errors.serverError("OpenAPI document is not available");
        }
        return Response.ok(document).type(MediaType.APPLICATION_JSON).build();
    }
}

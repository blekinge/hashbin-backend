package net.hashbin.rest;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SecuritySchemeType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.security.SecurityScheme;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import net.hashbin.model.Timestamp;
import net.hashbin.rest.dto.TimestampView;
import net.hashbin.service.AuthService;

/**
 * GET /api/my-timestamps — list the authenticated user's own timestamps (full
 * projection, newest first). Requires a valid bearer token.
 */
@Path("/my-timestamps")
@Tag(name = "Timestamps")
@SecurityScheme(securitySchemeName = "bearerAuth", type = SecuritySchemeType.HTTP,
        scheme = "bearer", bearerFormat = "JWT", description = "Supabase JWT access token")
public class MyTimestampsResource {

    @Inject
    AuthService auth;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Transactional
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "List the authenticated user's timestamps")
    @APIResponse(responseCode = "200", description = "Array of the caller's timestamps (full projection)",
            content = @Content(schema = @Schema(implementation = TimestampView[].class)))
    @APIResponse(responseCode = "401", description = "Authentication required")
    public Response myTimestamps(@HeaderParam("Authorization") String authorization) {
        UUID userId = auth.resolveUserId(authorization).orElse(null);
        if (userId == null) {
            return Errors.json(401, "Authentication required");
        }
        List<Timestamp> rows = Timestamp.list("userId = ?1 ORDER BY createdAt DESC", userId);
        List<TimestampView> result = rows.stream().map(TimestampView::from).collect(Collectors.toList());
        return Response.ok(result).build();
    }
}

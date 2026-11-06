package net.hashbin.rest;

import java.util.UUID;
import java.util.regex.Pattern;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import net.hashbin.model.AppUser;
import net.hashbin.rest.dto.AuthRequest;
import net.hashbin.rest.dto.AuthResponse;
import net.hashbin.rest.dto.MeView;
import net.hashbin.service.EmailAlreadyExistsException;
import net.hashbin.service.JwtService;
import net.hashbin.service.UserService;

/**
 * Native email + password authentication (Phase 6), replacing Supabase Auth.
 * Issues the service's own HS256 JWTs (see {@link JwtService}).
 */
@Path("/auth")
@Tag(name = "Auth")
public class AuthResource {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final int MIN_PASSWORD = 8;
    private static final int MAX_PASSWORD = 72; // BCrypt truncates beyond this

    @Inject
    UserService users;

    @Inject
    JwtService jwt;

    @POST
    @Path("/register")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Register a new account and return an access token")
    @APIResponse(responseCode = "201", description = "Account created; returns an access token",
            content = @Content(schema = @Schema(implementation = AuthResponse.class)))
    @APIResponse(responseCode = "409", description = "Email already registered")
    public Response register(AuthRequest req) {
        String invalid = validate(req);
        if (invalid != null) {
            return Errors.badRequest(invalid);
        }
        try {
            AppUser user = users.register(req.email, req.password);
            String token = jwt.issue(user.id, user.email);
            return Response.status(201)
                    .entity(AuthResponse.of(token, user.id, user.email, jwt.tokenTtlSeconds()))
                    .build();
        } catch (EmailAlreadyExistsException dup) {
            return Errors.json(409, "Email already registered");
        }
    }

    @POST
    @Path("/login")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Exchange email + password for an access token")
    @APIResponse(responseCode = "200", description = "Login succesful, returns an access token",
                 content = @Content(schema = @Schema(implementation = AuthResponse.class)))
    @APIResponse(responseCode = "401", description = "Invalid email or password")
    public Response login(AuthRequest req) {
        if (req == null || isBlank(req.email) || isBlank(req.password)) {
            return Errors.json(401, "Invalid email or password");
        }
        return users.authenticate(req.email, req.password)
                .map(u -> Response.ok(AuthResponse.of(
                        jwt.issue(u.id, u.email), u.id, u.email, jwt.tokenTtlSeconds())).build())
                .orElseGet(() -> Errors.json(401, "Invalid email or password"));
    }

    @GET
    @Path("/me")
    @Produces(MediaType.APPLICATION_JSON)
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Return the authenticated user's profile")
    @APIResponse(responseCode = "200", description = "The authenticated user",
            content = @Content(schema = @Schema(implementation = MeView.class)))
    @APIResponse(responseCode = "401", description = "Authentication required")
    public Response me(@HeaderParam("Authorization") String authorization) {
        UUID userId = jwt.resolveUserId(authorization).orElse(null);
        if (userId == null) {
            return Errors.json(401, "Authentication required");
        }
        return users.findById(userId)
                .map(u -> Response.ok(MeView.of(u.id, u.email)).build())
                .orElseGet(() -> Errors.json(401, "Authentication required"));
    }

    private static String validate(AuthRequest req) {
        if (req == null || isBlank(req.email) || !EMAIL.matcher(req.email.trim()).matches()) {
            return "Invalid email";
        }
        if (req.password == null || req.password.length() < MIN_PASSWORD || req.password.length() > MAX_PASSWORD) {
            return "Password must be between 8 and 72 characters";
        }
        return null;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}

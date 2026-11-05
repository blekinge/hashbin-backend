package net.hashbin.rest;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import jakarta.transaction.Transactional;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import net.hashbin.model.Timestamp;
import net.hashbin.rest.dto.TimestampPublicView;

/**
 * GET /api/verify — look up every timestamp matching a hash. The hash algorithm
 * is auto-detected from the hex length, or given explicitly. Results are the
 * public projection (no user/file_name), oldest first.
 */
@Path("/verify")
@Tag(name = "Timestamps")
public class VerifyResource {

    private static final Pattern MD5 = Pattern.compile("^[a-f0-9]{32}$");
    private static final Pattern SHA1 = Pattern.compile("^[a-f0-9]{40}$");
    private static final Pattern SHA256 = Pattern.compile("^[a-f0-9]{64}$");
    private static final Pattern SHA512 = Pattern.compile("^[a-f0-9]{128}$");

    /** algorithm query value -> Timestamp entity property (a safe whitelist). */
    private static final Map<String, String> ALGORITHMS = Map.of(
            "md5", "hashMd5",
            "sha1", "hashSha1",
            "sha-1", "hashSha1",
            "sha256", "hashSha256",
            "sha-256", "hashSha256",
            "sha512", "hashSha512",
            "sha-512", "hashSha512");

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Transactional
    @Operation(summary = "Look up timestamps for a hash",
            description = "Auto-detects the algorithm by hash length, or specify explicitly via the algorithm parameter.")
    @APIResponse(responseCode = "200", description = "Array of matching timestamps (may be empty)",
            content = @Content(schema = @Schema(implementation = TimestampPublicView[].class)))
    @APIResponse(responseCode = "400", description = "Invalid hash or unsupported algorithm")
    public Response verify(
            @QueryParam("hash") String hash,
            @Parameter(description = "auto|md5|sha1|sha256|sha512 (dash forms also accepted)")
            @QueryParam("algorithm") String algorithm) {

        if (hash == null || hash.isEmpty()) {
            return Errors.badRequest("Missing hash parameter");
        }
        String hex = hash.toLowerCase();

        String property;
        if (algorithm == null || algorithm.isEmpty() || "auto".equalsIgnoreCase(algorithm)) {
            property = detect(hex);
            if (property == null) {
                return Errors.badRequest("Invalid hash format");
            }
        } else {
            property = ALGORITHMS.get(algorithm.toLowerCase());
            if (property == null) {
                return Errors.badRequest("Unsupported algorithm");
            }
        }

        List<Timestamp> rows = Timestamp.list(property + " = ?1 ORDER BY createdAt ASC", hex);
        List<TimestampPublicView> result = rows.stream()
                .map(TimestampPublicView::from)
                .collect(Collectors.toList());
        return Response.ok(result).build();
    }

    private static String detect(String hex) {
        if (MD5.matcher(hex).matches()) {
            return "hashMd5";
        }
        if (SHA1.matcher(hex).matches()) {
            return "hashSha1";
        }
        if (SHA256.matcher(hex).matches()) {
            return "hashSha256";
        }
        if (SHA512.matcher(hex).matches()) {
            return "hashSha512";
        }
        return null;
    }
}

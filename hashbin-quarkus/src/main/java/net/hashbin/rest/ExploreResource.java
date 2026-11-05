package net.hashbin.rest;

import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import io.quarkus.hibernate.orm.panache.PanacheQuery;
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
 * GET /api/explore — the most recent public timestamps, for the frontend Explore
 * page. The original page read the Supabase {@code timestamps_public} view
 * directly; this replaces that with an API returning the same anonymized
 * projection ({@link TimestampPublicView}) newest-first, offset-paginated.
 *
 * <p>Paging mirrors the frontend's {@code range(from, to)} usage: {@code limit}
 * is the page size and {@code offset} the number of rows to skip.
 */
@Path("/explore")
@Tag(name = "Timestamps")
public class ExploreResource {

    /** Default page size — matches the Explore frontend's PAGE_SIZE. */
    private static final int DEFAULT_LIMIT = 20;

    /** Upper bound so a single request can't pull the whole table. */
    private static final int MAX_LIMIT = 100;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Transactional
    @Operation(summary = "List recent public timestamps",
            description = "Anonymized projection, newest first, offset-paginated. "
                    + "Replaces the Supabase timestamps_public view the Explore page used to read directly.")
    @APIResponse(responseCode = "200", description = "Array of public timestamps (may be empty)")
    @APIResponse(responseCode = "400", description = "Invalid limit or offset")
    public Response explore(
            @Parameter(description = "Page size, 1-100 (default " + DEFAULT_LIMIT + ")")
            @QueryParam("limit") String limit,
            @Parameter(description = "Rows to skip, >= 0 (default 0)")
            @QueryParam("offset") String offset) {

        int lim;
        if (limit == null || limit.isEmpty()) {
            lim = DEFAULT_LIMIT;
        } else {
            try {
                lim = Integer.parseInt(limit);
            } catch (NumberFormatException notAnInt) {
                return Errors.badRequest("Invalid limit parameter");
            }
            if (lim < 1 || lim > MAX_LIMIT) {
                return Errors.badRequest("limit must be between 1 and " + MAX_LIMIT);
            }
        }

        int off;
        if (offset == null || offset.isEmpty()) {
            off = 0;
        } else {
            try {
                off = Integer.parseInt(offset);
            } catch (NumberFormatException notAnInt) {
                return Errors.badRequest("Invalid offset parameter");
            }
            if (off < 0) {
                return Errors.badRequest("offset must be zero or greater");
            }
        }

        // Full JPQL (starts with SELECT) so Panache does not prepend a WHERE clause.
        // id DESC is a deterministic tiebreaker for rows sharing a created_at, which
        // keeps offset paging stable when two stamps land in the same millisecond.
        // The query is bound to PanacheQuery<Timestamp> so the entity type survives
        // the find(...).range(...).list() chain (otherwise T falls back to the base type).
        PanacheQuery<Timestamp> query = Timestamp
                .find("SELECT t FROM Timestamp t ORDER BY t.createdAt DESC, t.id DESC");
        List<TimestampPublicView> result = query
                .range(off, off + lim - 1)
                .list()
                .stream()
                .map(TimestampPublicView::from)
                .collect(Collectors.toList());
        return Response.ok(result).build();
    }
}

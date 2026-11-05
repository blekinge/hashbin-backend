package net.hashbin.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Resolves a Supabase auth user id by exchanging the bearer access token at
 * {@code {SUPABASE_URL}/auth/v1/user}.
 *
 * <p>This mirrors the original edge function, which calls
 * {@code supabase.auth.getUser()}. We deliberately do NOT verify the JWT locally:
 * Supabase signs tokens with HS256 using a shared secret, and the round-trip is
 * both algorithm-agnostic and revocation-aware. Any problem (missing/invalid
 * token, non-2xx response, network error) yields {@link Optional#empty()} so the
 * stamp endpoint degrades to anonymous rather than failing.
 */
@ApplicationScoped
public class SupabaseAuthService implements AuthService {

    @ConfigProperty(name = "hashbin.supabase.url", defaultValue = "http://localhost:54321")
    String supabaseUrl;

    @ConfigProperty(name = "hashbin.supabase.anon-key")
    Optional<String> anonKey;

    @Inject
    ObjectMapper mapper;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Override
    public Optional<UUID> resolveUserId(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return Optional.empty();
        }
        String bearer = authorization.substring("Bearer ".length()).trim();
        if (bearer.isEmpty()) {
            return Optional.empty();
        }

        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(trimTrailingSlash(supabaseUrl) + "/auth/v1/user"))
                    .timeout(Duration.ofSeconds(5))
                    .header("Authorization", "Bearer " + bearer)
                    .GET();
            anonKey.filter(k -> !k.isBlank()).ifPresent(k -> builder.header("apikey", k));

            HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return Optional.empty();
            }

            JsonNode user = mapper.readTree(response.body());
            JsonNode id = user.get("id");
            if (id == null || id.isNull()) {
                return Optional.empty();
            }
            return Optional.of(UUID.fromString(id.asText()));
        } catch (Exception any) {
            // Token invalid, Supabase unreachable, or response malformed → anonymous.
            return Optional.empty();
        }
    }

    private static String trimTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}

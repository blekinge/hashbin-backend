package net.hashbin.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Issues and verifies the service's own HS256 JWTs, replacing Supabase-issued
 * tokens. The {@link AuthService} contract is fulfilled by {@link #resolveUserId}:
 * a valid bearer resolves to the {@code sub} claim (the {@link AppUser} id); any
 * invalid/absent token resolves to empty so stamps stay anonymous and
 * {@code /my-timestamps} returns 401.
 *
 * <p>The MAC key is {@code SHA-256(secret)}, so any-length secret is accepted and
 * a wrong secret simply fails verification.
 */
@ApplicationScoped
public class JwtService implements AuthService {

    @ConfigProperty(name = "hashbin.auth.jwt-secret")
    String secret;

    @ConfigProperty(name = "hashbin.auth.jwt-issuer", defaultValue = "hashbin")
    String issuer;

    @ConfigProperty(name = "hashbin.auth.token-ttl-minutes", defaultValue = "1440")
    long tokenTtlMinutes;

    /** Mints a signed access token for a user. */
    public String issue(UUID userId, String email) {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(userId.toString())
                .issuer(issuer)
                .claim("email", email)
                .jwtID(UUID.randomUUID().toString())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(tokenTtlMinutes * 60)))
                .build();
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.HS256).type(JOSEObjectType.JWT).build(),
                claims);
        try {
            jwt.sign(new MACSigner(key()));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException("Failed to sign JWT", e);
        }
    }

    /** Remaining lifetime in seconds of a freshly issued token. */
    public long tokenTtlSeconds() {
        return tokenTtlMinutes * 60;
    }

    @Override
    public Optional<UUID> resolveUserId(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return Optional.empty();
        }
        return verify(authorization.substring("Bearer ".length()).trim());
    }

    /** Verifies a serialized JWT and returns its subject, or empty if invalid. */
    public Optional<UUID> verify(String token) {
        if (token.isEmpty()) {
            return Optional.empty();
        }
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            if (!jwt.getHeader().getAlgorithm().equals(JWSAlgorithm.HS256)) {
                return Optional.empty();
            }
            if (!jwt.verify(new MACVerifier(key()))) {
                return Optional.empty();
            }
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            Date exp = claims.getExpirationTime();
            if (exp == null || exp.toInstant().isBefore(Instant.now())) {
                return Optional.empty();
            }
            if (!issuer.equals(claims.getIssuer())) {
                return Optional.empty();
            }
            return Optional.of(UUID.fromString(claims.getSubject()));
        } catch (java.text.ParseException | JOSEException | IllegalArgumentException invalid) {
            return Optional.empty();
        }
    }

    private byte[] key() {
        try {
            return MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}

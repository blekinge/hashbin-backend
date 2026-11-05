package net.hashbin.service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import io.quarkus.elytron.security.common.BcryptUtil;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import net.hashbin.model.AppUser;

/**
 * Native account lifecycle: registration (with BCrypt password hashing) and
 * credential checks. Replaces the Supabase {@code auth.signUp}/{@code signInWithPassword}
 * calls the original frontend made.
 */
@ApplicationScoped
public class UserService {

    /**
     * Creates a user.
     *
     * @throws EmailAlreadyExistsException if the (normalised) e-mail is taken
     */
    @Transactional
    public AppUser register(String rawEmail, String password) {
        String email = normalize(rawEmail);
        if (AppUser.find("email", email).count() > 0) {
            throw new EmailAlreadyExistsException();
        }
        AppUser user = new AppUser();
        user.id = UUID.randomUUID();
        user.email = email;
        user.passwordHash = BcryptUtil.bcryptHash(password);
        user.createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        user.persist();
        return user;
    }

    /** @return the user when the e-mail/password match, otherwise empty. */
    @Transactional
    public Optional<AppUser> authenticate(String rawEmail, String password) {
        AppUser user = AppUser.find("email", normalize(rawEmail)).firstResult();
        if (user == null || !BcryptUtil.matches(password, user.passwordHash)) {
            return Optional.empty();
        }
        return Optional.of(user);
    }

    @Transactional
    public Optional<AppUser> findById(UUID id) {
        return Optional.ofNullable(AppUser.findById(id));
    }

    static String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}

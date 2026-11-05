package net.hashbin.model;

import java.time.OffsetDateTime;
import java.util.UUID;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A native user account. Replaces the dependency on Supabase's {@code auth.users}
 * so the service can register, authenticate, and issue its own tokens.
 */
@Entity
@Table(name = "app_users")
public class AppUser extends PanacheEntityBase {

    @Id
    @Column(name = "id")
    public UUID id;

    /** Lower-cased e-mail; unique via {@code uq_app_users_email}. */
    @Column(name = "email", nullable = false, unique = true)
    public String email;

    @Column(name = "password_hash", nullable = false)
    public String passwordHash;

    @Column(name = "created_at", nullable = false)
    public OffsetDateTime createdAt;
}

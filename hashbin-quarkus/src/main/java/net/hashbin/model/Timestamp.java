package net.hashbin.model;

import java.time.OffsetDateTime;
import java.util.UUID;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A single timestamp record: a set of file digests plus the server-side
 * HMAC signature over "{hash_sha256}:{created_at}".
 *
 * <p>The files themselves are never stored — only their hashes, hashed
 * client-side by the browser or CLI before submission.
 */
@Entity
@Table(name = "timestamps")
public class Timestamp extends PanacheEntityBase {

    @Id
    @Column(name = "id")
    public UUID id;

    @Column(name = "hash_sha256", nullable = false)
    public String hashSha256;

    @Column(name = "hash_md5")
    public String hashMd5;

    @Column(name = "hash_sha1")
    public String hashSha1;

    @Column(name = "hash_sha512")
    public String hashSha512;

    @Column(name = "file_size", nullable = false)
    public long fileSize;

    @Column(name = "file_name")
    public String fileName;

    @Column(name = "created_at", nullable = false)
    public OffsetDateTime createdAt;

    @Column(name = "server_signature", nullable = false)
    public String serverSignature;

    /**
     * Optional owner (Supabase auth user id). Null for anonymous stamps.
     * Stored as a bare UUID rather than a JPA relation so the service stays
     * decoupled from Supabase's auth.users schema.
     */
    @Column(name = "user_id")
    public UUID userId;
}

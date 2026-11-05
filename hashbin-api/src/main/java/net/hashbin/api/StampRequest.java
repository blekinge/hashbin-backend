package net.hashbin.api;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The JSON body for {@code POST /api/stamp}. SHA-256 is required; MD5/SHA-1/
 * SHA-512 and the file name are optional and simply omitted when unset. Field
 * names match the wire contract (snake_case) when {@link #toBody()} is called.
 *
 * <p>The caller is responsible for computing the digests (this library keeps no
 * file-hashing concern — it is the API client, not a CLI). A convenience is
 * provided for the common case where only the SHA-256 is known.
 */
public final class StampRequest {

    private final String sha256;
    private final String md5;
    private final String sha1;
    private final String sha512;
    private final long fileSize;
    private final String fileName;

    private StampRequest(Builder b) {
        this.sha256 = b.sha256;
        this.md5 = b.md5;
        this.sha1 = b.sha1;
        this.sha512 = b.sha512;
        this.fileSize = b.fileSize;
        this.fileName = b.fileName;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** The body exactly as sent: lowercase-hex digests, snake_case keys, nulls omitted. */
    public Map<String, Object> toBody() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("hash_sha256", sha256);
        if (md5 != null) {
            body.put("hash_md5", md5);
        }
        if (sha1 != null) {
            body.put("hash_sha1", sha1);
        }
        if (sha512 != null) {
            body.put("hash_sha512", sha512);
        }
        body.put("file_size", fileSize);
        if (fileName != null) {
            body.put("file_name", fileName);
        }
        return body;
    }

    public static final class Builder {
        private String sha256;
        private String md5;
        private String sha1;
        private String sha512;
        private long fileSize;
        private String fileName;

        public Builder sha256(String v) { this.sha256 = v; return this; }
        public Builder md5(String v) { this.md5 = v; return this; }
        public Builder sha1(String v) { this.sha1 = v; return this; }
        public Builder sha512(String v) { this.sha512 = v; return this; }
        public Builder fileSize(long v) { this.fileSize = v; return this; }
        public Builder fileName(String v) { this.fileName = v; return this; }

        public StampRequest build() {
            if (sha256 == null || sha256.isEmpty()) {
                throw new IllegalArgumentException("SHA-256 hash is required");
            }
            return new StampRequest(this);
        }
    }
}

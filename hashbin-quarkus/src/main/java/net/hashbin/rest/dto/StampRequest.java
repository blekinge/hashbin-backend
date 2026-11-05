package net.hashbin.rest.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.quarkus.runtime.annotations.RegisterForReflection;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * OpenAPI schema for the {@code POST /api/stamp} request body.
 *
 * <p>{@link net.hashbin.rest.StampResource} deliberately reads the body as a raw
 * {@code String} and validates it field-by-field to preserve the original edge
 * function's exact validation order and error strings. This type is NOT bound to
 * that request; it exists purely so the generated OpenAPI document carries a
 * typed, code-gen-able request body (see {@code OpenApiSpecResourceTest}). Keep the
 * property names in sync with the {@code hash_*} / {@code file_*} keys the resource
 * reads, and with the client's {@code net.hashbin.api.StampRequest}.
 */
@RegisterForReflection
@Schema(name = "StampRequest",
        description = "File digests and metadata for a new timestamp. Only hash_sha256 and "
                + "file_size are required; the other digests and file_name are optional.")
public class StampRequest {

    @Schema(description = "SHA-256 digest, lowercase hex (64 chars).", required = true,
            example = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855")
    @JsonProperty("hash_sha256")
    public String hashSha256;

    @Schema(description = "MD5 digest, lowercase hex (32 chars).", nullable = true, example = "d41d8cd98f00b204e9800998ecf8427e")
    @JsonProperty("hash_md5")
    public String hashMd5;

    @Schema(description = "SHA-1 digest, lowercase hex (40 chars).", nullable = true, example = "da39a3ee5e6b4b0d3255bfef95601890afd80709")
    @JsonProperty("hash_sha1")
    public String hashSha1;

    @Schema(description = "SHA-512 digest, lowercase hex (128 chars).", nullable = true)
    @JsonProperty("hash_sha512")
    public String hashSha512;

    @Schema(description = "File size in bytes; non-negative, up to 10 TiB.", required = true, example = "1048576")
    @JsonProperty("file_size")
    public long fileSize;

    @Schema(description = "Optional original file name (up to 500 chars).", nullable = true, example = "report.pdf")
    @JsonProperty("file_name")
    public String fileName;
}

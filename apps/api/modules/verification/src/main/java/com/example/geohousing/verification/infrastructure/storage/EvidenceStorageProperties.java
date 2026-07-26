package com.example.geohousing.verification.infrastructure.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Where the quarantined evidence bucket lives and how to reach it. Injected per environment — no
 * credentials are ever committed (AGENTS.md §3.10).
 *
 * @param endpoint S3-compatible endpoint; set for MinIO locally, left blank to use the SDK's
 *     default AWS resolution in production
 * @param region SDK region; MinIO ignores it but the SDK requires one
 * @param bucket the private, never-public evidence bucket
 * @param accessKeyId static credential; blank means "use the SDK's default provider chain"
 *     (instance role, environment, profile) rather than a credential in configuration
 * @param secretAccessKey partner of {@code accessKeyId}
 * @param maxUploadBytes hard cap on a single evidence object; a stream larger than this is refused
 *     before it is stored
 * @param pathStyleAccess MinIO needs path-style addressing; most managed S3 services do not
 */
@ConfigurationProperties(prefix = "verification.evidence.storage")
public record EvidenceStorageProperties(
    String endpoint,
    String region,
    String bucket,
    String accessKeyId,
    String secretAccessKey,
    long maxUploadBytes,
    boolean pathStyleAccess) {

  public EvidenceStorageProperties {
    region = region == null || region.isBlank() ? "us-east-1" : region;
    maxUploadBytes = maxUploadBytes <= 0 ? 10L * 1024 * 1024 : maxUploadBytes;
  }

  boolean hasStaticCredentials() {
    return accessKeyId != null
        && !accessKeyId.isBlank()
        && secretAccessKey != null
        && !secretAccessKey.isBlank();
  }

  boolean hasEndpointOverride() {
    return endpoint != null && !endpoint.isBlank();
  }
}

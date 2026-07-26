package com.example.geohousing.verification.infrastructure.storage;

import java.net.URI;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * Builds the S3 client for the quarantined evidence bucket (ADR-0008).
 *
 * <p>Two deployment shapes are supported by configuration alone: an endpoint override with static
 * credentials (MinIO locally and in tests) and the SDK's default resolution (an instance role or
 * the environment) in production, so no credential ever needs to live in the repository.
 */
@Configuration
@EnableConfigurationProperties(EvidenceStorageProperties.class)
public class EvidenceStorageConfiguration {

  @Bean
  S3Client evidenceS3Client(EvidenceStorageProperties properties) {
    var builder =
        S3Client.builder()
            .region(Region.of(properties.region()))
            // MinIO needs path-style addressing; managed S3 uses virtual-hosted style.
            .forcePathStyle(properties.pathStyleAccess());

    if (properties.hasEndpointOverride()) {
      builder = builder.endpointOverride(URI.create(properties.endpoint()));
    }
    builder =
        properties.hasStaticCredentials()
            ? builder.credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(
                        properties.accessKeyId(), properties.secretAccessKey())))
            // No credentials configured: let the SDK resolve an instance role or the environment.
            : builder.credentialsProvider(DefaultCredentialsProvider.builder().build());

    return builder.build();
  }
}

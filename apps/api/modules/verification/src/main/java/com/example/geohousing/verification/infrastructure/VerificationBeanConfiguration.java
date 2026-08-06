package com.example.geohousing.verification.infrastructure;

import com.example.geohousing.verification.application.EvidenceAccessAuditRepository;
import com.example.geohousing.verification.application.EvidenceRepository;
import com.example.geohousing.verification.application.EvidenceRetentionPolicy;
import com.example.geohousing.verification.application.EvidenceRetentionService;
import com.example.geohousing.verification.application.EvidenceService;
import com.example.geohousing.verification.application.EvidenceStore;
import com.example.geohousing.verification.application.PropertyLookup;
import com.example.geohousing.verification.application.ReviewProjection;
import com.example.geohousing.verification.application.VerificationCaseRepository;
import com.example.geohousing.verification.application.VerificationDecisionRepository;
import com.example.geohousing.verification.application.VerificationDecisionService;
import com.example.geohousing.verification.application.VerificationExpiryService;
import com.example.geohousing.verification.application.VerificationQueryService;
import com.example.geohousing.verification.application.VerificationSubmissionService;
import com.example.geohousing.verification.infrastructure.storage.EvidenceStorageProperties;
import java.time.Clock;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Constructs the verification module's framework-free application services as Spring beans. The
 * adapters they depend on ({@code JpaVerificationCaseRepository}, {@code
 * JpaVerificationDecisionRepository}, {@code CatalogPropertyResolver}, {@code
 * ReviewProjectionAdapter}) are component-scanned.
 *
 * <p>The clock is constructed inline rather than exposed as a bean, for the same reason as in the
 * other modules: the convention build does not compile with {@code -parameters}, so a second {@code
 * Clock} bean would make every {@code Clock} injection ambiguous.
 */
@Configuration
@EnableConfigurationProperties({
  EvidenceRetentionProperties.class,
  VerificationExpiryProperties.class
})
@EnableScheduling
public class VerificationBeanConfiguration {

  @Bean
  VerificationSubmissionService verificationSubmissionService(
      VerificationCaseRepository caseRepository,
      PropertyLookup propertyLookup,
      EvidenceService evidenceService) {
    return new VerificationSubmissionService(
        caseRepository, propertyLookup, evidenceService, Clock.systemUTC());
  }

  @Bean
  VerificationDecisionService verificationDecisionService(
      VerificationCaseRepository caseRepository,
      EvidenceRepository evidenceRepository,
      VerificationDecisionRepository decisionRepository,
      ReviewProjection reviewProjection,
      EvidenceService evidenceService) {
    return new VerificationDecisionService(
        caseRepository,
        evidenceRepository,
        decisionRepository,
        reviewProjection,
        evidenceService,
        Clock.systemUTC());
  }

  @Bean
  VerificationQueryService verificationQueryService(VerificationCaseRepository caseRepository) {
    return new VerificationQueryService(caseRepository);
  }

  @Bean
  VerificationExpiryService verificationExpiryService(
      VerificationCaseRepository caseRepository,
      VerificationDecisionRepository decisionRepository,
      ReviewProjection reviewProjection) {
    return new VerificationExpiryService(
        caseRepository, decisionRepository, reviewProjection, Clock.systemUTC());
  }

  @Bean
  EvidenceRetentionPolicy evidenceRetentionPolicy(EvidenceRetentionProperties properties) {
    return new EvidenceRetentionPolicy(
        Duration.ofDays(properties.uploadRetentionDays()),
        Duration.ofDays(properties.postDecisionRetentionDays()));
  }

  @Bean
  EvidenceService evidenceService(
      VerificationCaseRepository caseRepository,
      EvidenceRepository evidenceRepository,
      EvidenceAccessAuditRepository accessAudit,
      EvidenceStore evidenceStore,
      EvidenceRetentionPolicy retentionPolicy,
      EvidenceStorageProperties storageProperties) {
    return new EvidenceService(
        caseRepository,
        evidenceRepository,
        accessAudit,
        evidenceStore,
        retentionPolicy,
        storageProperties.maxUploadBytes(),
        Clock.systemUTC());
  }

  @Bean
  EvidenceRetentionService evidenceRetentionService(
      EvidenceRepository evidenceRepository,
      EvidenceAccessAuditRepository accessAudit,
      EvidenceStore evidenceStore) {
    return new EvidenceRetentionService(
        evidenceRepository, accessAudit, evidenceStore, Clock.systemUTC());
  }
}

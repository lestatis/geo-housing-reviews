package com.example.geohousing.verification.infrastructure;

import com.example.geohousing.verification.application.PropertyLookup;
import com.example.geohousing.verification.application.ReviewProjection;
import com.example.geohousing.verification.application.VerificationCaseRepository;
import com.example.geohousing.verification.application.VerificationDecisionRepository;
import com.example.geohousing.verification.application.VerificationDecisionService;
import com.example.geohousing.verification.application.VerificationExpiryService;
import com.example.geohousing.verification.application.VerificationQueryService;
import com.example.geohousing.verification.application.VerificationSubmissionService;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
public class VerificationBeanConfiguration {

  @Bean
  VerificationSubmissionService verificationSubmissionService(
      VerificationCaseRepository caseRepository, PropertyLookup propertyLookup) {
    return new VerificationSubmissionService(caseRepository, propertyLookup, Clock.systemUTC());
  }

  @Bean
  VerificationDecisionService verificationDecisionService(
      VerificationCaseRepository caseRepository,
      VerificationDecisionRepository decisionRepository,
      ReviewProjection reviewProjection) {
    return new VerificationDecisionService(
        caseRepository, decisionRepository, reviewProjection, Clock.systemUTC());
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
}

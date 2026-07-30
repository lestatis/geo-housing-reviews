package com.example.geohousing.moderation.infrastructure;

import com.example.geohousing.moderation.application.ModerationCaseRepository;
import com.example.geohousing.moderation.application.ModerationCaseService;
import com.example.geohousing.moderation.application.ModerationDecisionRepository;
import com.example.geohousing.moderation.application.ModerationEffectApplier;
import com.example.geohousing.moderation.application.ModerationTargetLookup;
import com.example.geohousing.moderation.application.ReportIntakeService;
import com.example.geohousing.moderation.application.ReportRepository;
import com.example.geohousing.moderation.domain.PolicyVersion;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Constructs the moderation module's framework-free application services as Spring beans. The
 * adapters they depend on (the JPA repositories and the two reviews adapters) are
 * component-scanned.
 *
 * <p>The clock is constructed inline rather than exposed as a bean, for the same reason as in the
 * reviews and properties modules: the convention build does not compile with {@code -parameters},
 * so a second {@code Clock} bean would make every {@code Clock} injection ambiguous.
 */
@Configuration
public class ModerationBeanConfiguration {

  @Bean
  ReportIntakeService reportIntakeService(
      ReportRepository reportRepository,
      ModerationCaseRepository caseRepository,
      ModerationTargetLookup targetLookup) {
    return new ReportIntakeService(
        reportRepository, caseRepository, targetLookup, Clock.systemUTC());
  }

  /**
   * The content policy version every decision is stamped with. Configurable rather than hardcoded
   * because an appeal must be judged under the policy that was in force when the decision was made
   * (MODERATION.md), so raising it is an operational act, not a code change.
   */
  @Bean
  ModerationCaseService moderationCaseService(
      ModerationCaseRepository caseRepository,
      ModerationDecisionRepository decisionRepository,
      ReportRepository reportRepository,
      ModerationTargetLookup targetLookup,
      ModerationEffectApplier effectApplier,
      @Value("${moderation.policy-version:1}") int policyVersion) {
    return new ModerationCaseService(
        caseRepository,
        decisionRepository,
        reportRepository,
        targetLookup,
        effectApplier,
        PolicyVersion.of(policyVersion),
        Clock.systemUTC());
  }
}

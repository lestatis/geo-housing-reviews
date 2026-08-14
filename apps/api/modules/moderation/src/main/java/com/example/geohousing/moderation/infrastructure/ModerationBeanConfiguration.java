package com.example.geohousing.moderation.infrastructure;

import com.example.geohousing.identity.api.AccountStanding;
import com.example.geohousing.moderation.api.AppealUseCase;
import com.example.geohousing.moderation.api.ModerationCaseUseCase;
import com.example.geohousing.moderation.application.AppealRepository;
import com.example.geohousing.moderation.application.AppealService;
import com.example.geohousing.moderation.application.ModerationCaseRepository;
import com.example.geohousing.moderation.application.ModerationCaseService;
import com.example.geohousing.moderation.application.ModerationDecisionRepository;
import com.example.geohousing.moderation.application.ModerationEffectApplier;
import com.example.geohousing.moderation.application.ModerationQueueService;
import com.example.geohousing.moderation.application.ModerationTargetLookup;
import com.example.geohousing.moderation.application.ReportIntakeService;
import com.example.geohousing.moderation.application.ReportQueryService;
import com.example.geohousing.moderation.application.ReportRepository;
import com.example.geohousing.moderation.domain.PolicyVersion;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

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
      ModerationTargetLookup targetLookup,
      AccountStanding accountStanding) {
    return new ReportIntakeService(
        reportRepository, caseRepository, targetLookup, accountStanding, Clock.systemUTC());
  }

  @Bean
  AppealService appealService(
      AppealRepository appealRepository,
      ModerationCaseRepository caseRepository,
      ModerationDecisionRepository decisionRepository,
      ModerationTargetLookup targetLookup,
      ModerationEffectApplier effectApplier) {
    return new AppealService(
        appealRepository,
        caseRepository,
        decisionRepository,
        targetLookup,
        effectApplier,
        Clock.systemUTC());
  }

  @Bean
  ModerationQueueService moderationQueueService(
      ModerationCaseRepository caseRepository,
      ReportRepository reportRepository,
      ModerationDecisionRepository decisionRepository) {
    return new ModerationQueueService(caseRepository, reportRepository, decisionRepository);
  }

  @Bean
  ReportQueryService reportQueryService(ReportRepository reportRepository) {
    return new ReportQueryService(reportRepository);
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

  /** What the controllers depend on: the rules, inside the transaction that makes them hold. */
  @Bean
  @Primary
  ModerationCaseUseCase moderationCaseUseCase(ModerationCaseService moderationCaseService) {
    return new TransactionalModerationCaseService(moderationCaseService);
  }

  @Bean
  @Primary
  AppealUseCase appealUseCase(AppealService appealService) {
    return new TransactionalAppealService(appealService);
  }
}

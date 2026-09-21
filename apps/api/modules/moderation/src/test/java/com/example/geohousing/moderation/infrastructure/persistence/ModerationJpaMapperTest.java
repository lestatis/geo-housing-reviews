package com.example.geohousing.moderation.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.geohousing.moderation.domain.DecisionAction;
import com.example.geohousing.moderation.domain.ModerationCaseId;
import com.example.geohousing.moderation.domain.ModerationDecision;
import com.example.geohousing.moderation.domain.ModerationDecisionId;
import com.example.geohousing.moderation.domain.ModeratorId;
import com.example.geohousing.moderation.domain.PolicyVersion;
import com.example.geohousing.moderation.domain.ReasonCode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ModerationJpaMapperTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-08-17T10:00:00Z"), ZoneOffset.UTC);

  @Test
  void aDecisionKeepsTheRestrictionItCreatedAcrossPersistenceMapping() {
    UUID restrictionId = UUID.randomUUID();
    ModerationDecision decision =
        ModerationDecision.record(
                ModerationDecisionId.of(UUID.randomUUID()),
                ModerationCaseId.of(UUID.randomUUID()),
                DecisionAction.RESTRICT_ACCOUNT,
                ReasonCode.of("HARASSMENT"),
                PolicyVersion.of(1),
                "Your reports targeted another resident.",
                null,
                3L,
                ModeratorId.of(UUID.randomUUID()),
                CLOCK)
            .withCreatedRestriction(restrictionId);

    ModerationDecision restored =
        ModerationJpaMapper.toDomain(ModerationJpaMapper.toEntity(decision));

    assertThat(restored.createdRestrictionId()).contains(restrictionId);
  }

  @Test
  void aHistoricalDecisionWithNoLinkRemainsAnUnownedRestriction() {
    ModerationDecision decision =
        ModerationDecision.record(
            ModerationDecisionId.of(UUID.randomUUID()),
            ModerationCaseId.of(UUID.randomUUID()),
            DecisionAction.RESTRICT_ACCOUNT,
            ReasonCode.of("HARASSMENT"),
            PolicyVersion.of(1),
            "Your reports targeted another resident.",
            null,
            3L,
            ModeratorId.of(UUID.randomUUID()),
            CLOCK);

    ModerationDecision restored =
        ModerationJpaMapper.toDomain(ModerationJpaMapper.toEntity(decision));

    assertThat(restored.createdRestrictionId()).isEmpty();
  }
}

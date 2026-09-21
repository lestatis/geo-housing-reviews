package com.example.geohousing.moderation.infrastructure.persistence;

import com.example.geohousing.moderation.domain.Appeal;
import com.example.geohousing.moderation.domain.AppealId;
import com.example.geohousing.moderation.domain.AppealStatus;
import com.example.geohousing.moderation.domain.AppellantId;
import com.example.geohousing.moderation.domain.CaseTrigger;
import com.example.geohousing.moderation.domain.DecisionAction;
import com.example.geohousing.moderation.domain.ModerationCase;
import com.example.geohousing.moderation.domain.ModerationCaseId;
import com.example.geohousing.moderation.domain.ModerationCaseStatus;
import com.example.geohousing.moderation.domain.ModerationDecision;
import com.example.geohousing.moderation.domain.ModerationDecisionId;
import com.example.geohousing.moderation.domain.ModerationTargetRef;
import com.example.geohousing.moderation.domain.ModerationTargetType;
import com.example.geohousing.moderation.domain.ModeratorId;
import com.example.geohousing.moderation.domain.PolicyVersion;
import com.example.geohousing.moderation.domain.ReasonCode;
import com.example.geohousing.moderation.domain.Report;
import com.example.geohousing.moderation.domain.ReportCategory;
import com.example.geohousing.moderation.domain.ReportId;
import com.example.geohousing.moderation.domain.ReportStatus;
import com.example.geohousing.moderation.domain.ReporterId;
import com.example.geohousing.moderation.domain.RiskLevel;

/**
 * Translates between the domain aggregates and their rows.
 *
 * <p>Enums are stored by name rather than ordinal: the schema's CHECK constraints spell the values
 * out, and an ordinal would silently remap every stored row the moment a constant is inserted.
 */
final class ModerationJpaMapper {

  private ModerationJpaMapper() {}

  static ModerationCaseJpaEntity toEntity(ModerationCase moderationCase) {
    return new ModerationCaseJpaEntity(
        moderationCase.id().value(),
        moderationCase.target().type().name(),
        moderationCase.target().id(),
        moderationCase.trigger().name(),
        moderationCase.status().name(),
        moderationCase.riskLevel().name(),
        moderationCase.assignedModerator().map(ModeratorId::value).orElse(null),
        moderationCase.openedAt(),
        moderationCase.firstResponseAt().orElse(null),
        moderationCase.closedAt().orElse(null),
        moderationCase.createdAt(),
        moderationCase.updatedAt(),
        moderationCase.version());
  }

  static ModerationCase toDomain(ModerationCaseJpaEntity entity) {
    return ModerationCase.reconstitute(
        ModerationCaseId.of(entity.id()),
        new ModerationTargetRef(
            ModerationTargetType.valueOf(entity.targetType()), entity.targetId()),
        CaseTrigger.valueOf(entity.triggerSource()),
        ModerationCaseStatus.valueOf(entity.status()),
        RiskLevel.valueOf(entity.riskLevel()),
        entity.assignedModeratorAccountId() == null
            ? null
            : ModeratorId.of(entity.assignedModeratorAccountId()),
        entity.openedAt(),
        entity.firstResponseAt(),
        entity.closedAt(),
        entity.createdAt(),
        entity.updatedAt(),
        entity.version());
  }

  static ReportJpaEntity toEntity(Report report) {
    return new ReportJpaEntity(
        report.id().value(),
        report.target().type().name(),
        report.target().id(),
        report.reporterId().value(),
        report.category().name(),
        report.description().orElse(null),
        report.status().name(),
        report.caseId().map(ModerationCaseId::value).orElse(null),
        report.createdAt());
  }

  static Report toDomain(ReportJpaEntity entity) {
    return Report.reconstitute(
        ReportId.of(entity.id()),
        new ModerationTargetRef(
            ModerationTargetType.valueOf(entity.targetType()), entity.targetId()),
        ReporterId.of(entity.reporterAccountId()),
        ReportCategory.valueOf(entity.category()),
        entity.description(),
        ReportStatus.valueOf(entity.status()),
        entity.caseId() == null ? null : ModerationCaseId.of(entity.caseId()),
        entity.createdAt());
  }

  static ModerationDecisionJpaEntity toEntity(ModerationDecision decision) {
    return new ModerationDecisionJpaEntity(
        decision.createdRestrictionId().orElse(null),
        decision.id().value(),
        decision.caseId().value(),
        decision.action().name(),
        decision.reasonCode().value(),
        decision.policyVersion().value(),
        decision.publicExplanation().orElse(null),
        decision.internalNote().orElse(null),
        decision.affectedTargetVersion().orElse(null),
        decision.decidedBy().value(),
        decision.decidedAt());
  }

  static ModerationDecision toDomain(ModerationDecisionJpaEntity entity) {
    return ModerationDecision.reconstitute(
        ModerationDecisionId.of(entity.id()),
        ModerationCaseId.of(entity.caseId()),
        DecisionAction.valueOf(entity.action()),
        ReasonCode.of(entity.reasonCode()),
        PolicyVersion.of(entity.policyVersion()),
        entity.publicExplanation(),
        entity.internalNote(),
        entity.affectedTargetVersion(),
        ModeratorId.of(entity.decidedByAccountId()),
        entity.decidedAt(),
        entity.createdRestrictionId());
  }

  static AppealJpaEntity toEntity(Appeal appeal) {
    return new AppealJpaEntity(
        appeal.id().value(),
        appeal.decisionId().value(),
        appeal.appellantId().value(),
        appeal.appealText(),
        appeal.status().name(),
        appeal.outcomeExplanation().orElse(null),
        appeal.originalDecider().value(),
        appeal.decidedBy().map(ModeratorId::value).orElse(null),
        appeal.createdAt(),
        appeal.decidedAt().orElse(null),
        appeal.version());
  }

  static Appeal toDomain(AppealJpaEntity entity) {
    return Appeal.reconstitute(
        AppealId.of(entity.id()),
        ModerationDecisionId.of(entity.decisionId()),
        AppellantId.of(entity.appellantAccountId()),
        entity.appealText(),
        AppealStatus.valueOf(entity.status()),
        entity.outcomeExplanation(),
        ModeratorId.of(entity.originalDeciderAccountId()),
        entity.decidedByAccountId() == null ? null : ModeratorId.of(entity.decidedByAccountId()),
        entity.createdAt(),
        entity.decidedAt(),
        entity.version());
  }
}

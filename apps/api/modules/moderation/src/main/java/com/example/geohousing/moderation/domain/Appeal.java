package com.example.geohousing.moderation.domain;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * One structured challenge to a decision (docs/DOMAIN_MODEL.md Appeal; MODERATION.md appeals).
 *
 * <p>Exactly one appeal exists per decision. A second bite is not an appeal, it is attrition — and
 * an appeals channel that can be worked repeatedly is one an organised party can use to wear down
 * moderation.
 *
 * <p>The appeal carries {@code originalDecider} so the rule that someone else must hear it can be
 * checked here, on this object, rather than by a caller that happens to remember to look the
 * decision up. The database enforces the same rule on the row; both exist because due process is
 * not something to leave to convention in one layer.
 */
public final class Appeal {

  private final AppealId id;
  private final ModerationDecisionId decisionId;
  private final AppellantId appellantId;
  private final String appealText;
  private AppealStatus status;
  private String outcomeExplanation;
  private final ModeratorId originalDecider;
  private ModeratorId decidedBy;
  private final Instant createdAt;
  private Instant decidedAt;
  private final long version;

  private Appeal(
      AppealId id,
      ModerationDecisionId decisionId,
      AppellantId appellantId,
      String appealText,
      AppealStatus status,
      String outcomeExplanation,
      ModeratorId originalDecider,
      ModeratorId decidedBy,
      Instant createdAt,
      Instant decidedAt,
      long version) {
    this.id = Objects.requireNonNull(id, "id");
    this.decisionId = Objects.requireNonNull(decisionId, "decisionId");
    this.appellantId = Objects.requireNonNull(appellantId, "appellantId");
    this.appealText = requireAppealText(appealText);
    this.status = Objects.requireNonNull(status, "status");
    this.outcomeExplanation = blankToNull(outcomeExplanation);
    this.originalDecider = Objects.requireNonNull(originalDecider, "originalDecider");
    this.decidedBy = decidedBy;
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    this.decidedAt = decidedAt;
    this.version = version;
    checkInvariants();
  }

  /** Files an appeal against a decision, to be heard by someone other than who made it. */
  public static Appeal file(
      AppealId id,
      ModerationDecision decision,
      AppellantId appellantId,
      String appealText,
      Clock clock) {
    Objects.requireNonNull(decision, "decision");
    Objects.requireNonNull(clock, "clock");
    return new Appeal(
        id,
        decision.id(),
        appellantId,
        appealText,
        AppealStatus.PENDING,
        null,
        decision.decidedBy(),
        null,
        clock.instant(),
        null,
        0L);
  }

  /** Rebuilds an appeal from persisted state. Intended for persistence adapters only. */
  public static Appeal reconstitute(
      AppealId id,
      ModerationDecisionId decisionId,
      AppellantId appellantId,
      String appealText,
      AppealStatus status,
      String outcomeExplanation,
      ModeratorId originalDecider,
      ModeratorId decidedBy,
      Instant createdAt,
      Instant decidedAt,
      long version) {
    return new Appeal(
        id,
        decisionId,
        appellantId,
        appealText,
        status,
        outcomeExplanation,
        originalDecider,
        decidedBy,
        createdAt,
        decidedAt,
        version);
  }

  /** Confirms the original decision. The appellant is still owed a reason. */
  public void uphold(ModeratorId moderatorId, String outcomeExplanation, Clock clock) {
    decide(AppealStatus.UPHELD, moderatorId, outcomeExplanation, clock);
  }

  /** Reverses the original decision. */
  public void overturn(ModeratorId moderatorId, String outcomeExplanation, Clock clock) {
    decide(AppealStatus.OVERTURNED, moderatorId, outcomeExplanation, clock);
  }

  private void decide(
      AppealStatus outcome, ModeratorId moderatorId, String explanation, Clock clock) {
    // Validate everything before touching any state, so a refused decision leaves the appeal
    // exactly PENDING rather than half-decided.
    if (status.isDecided()) {
      throw new IllegalModerationStateTransitionException(
          "an appeal is heard once; this one was already " + status);
    }
    Objects.requireNonNull(moderatorId, "moderatorId");
    if (moderatorId.equals(originalDecider)) {
      throw new AppealDeciderConflictException(
          "an appeal must be decided by someone other than the moderator who decided the case");
    }
    String reason = blankToNull(explanation);
    if (reason == null) {
      throw new IllegalArgumentException("an appeal outcome must explain itself to the appellant");
    }
    this.status = outcome;
    this.decidedBy = moderatorId;
    this.outcomeExplanation = reason;
    this.decidedAt = clock.instant();
    checkInvariants();
  }

  /** Whether this moderator may hear this appeal. */
  public boolean canBeDecidedBy(ModeratorId moderatorId) {
    return !status.isDecided() && !originalDecider.equals(moderatorId);
  }

  private static String requireAppealText(String appealText) {
    String trimmed = blankToNull(appealText);
    if (trimmed == null) {
      throw new IllegalArgumentException("an appeal must say what it is appealing");
    }
    return trimmed;
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private void checkInvariants() {
    if (status.isDecided() != (decidedAt != null)) {
      throw new IllegalStateException("an appeal is decided exactly when it records when");
    }
    if (status.isDecided() && (decidedBy == null || outcomeExplanation == null)) {
      throw new IllegalStateException("a decided appeal records who heard it and what they said");
    }
    if (decidedBy != null && decidedBy.equals(originalDecider)) {
      throw new IllegalStateException(
          "an appeal cannot be decided by the moderator appealed against");
    }
  }

  public AppealId id() {
    return id;
  }

  public ModerationDecisionId decisionId() {
    return decisionId;
  }

  public AppellantId appellantId() {
    return appellantId;
  }

  public String appealText() {
    return appealText;
  }

  public AppealStatus status() {
    return status;
  }

  public Optional<String> outcomeExplanation() {
    return Optional.ofNullable(outcomeExplanation);
  }

  public ModeratorId originalDecider() {
    return originalDecider;
  }

  public Optional<ModeratorId> decidedBy() {
    return Optional.ofNullable(decidedBy);
  }

  public Instant createdAt() {
    return createdAt;
  }

  public Optional<Instant> decidedAt() {
    return Optional.ofNullable(decidedAt);
  }

  public long version() {
    return version;
  }
}

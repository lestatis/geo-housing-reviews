package com.example.geohousing.moderation.application;

import com.example.geohousing.moderation.domain.Appeal;
import com.example.geohousing.moderation.domain.AppealDeciderConflictException;
import com.example.geohousing.moderation.domain.AppealId;
import com.example.geohousing.moderation.domain.AppellantId;
import com.example.geohousing.moderation.domain.ModerationCase;
import com.example.geohousing.moderation.domain.ModerationDecision;
import com.example.geohousing.moderation.domain.ModerationTargetRef;
import com.example.geohousing.moderation.domain.ModeratorId;
import com.example.geohousing.moderation.domain.ReasonCode;
import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * The author's route back: appeal a decision that took their content down, and have a different
 * moderator hear it.
 *
 * <p>An overturned appeal does not merely record that the decision was wrong — it undoes it.
 * Without that, a takedown demand that succeeds and then loses on appeal still gets what it wanted
 * (DECISION_LOG {@code P-014}).
 */
public final class AppealService {

  private static final ReasonCode APPEAL_OVERTURNED = ReasonCode.of("APPEAL_OVERTURNED");

  private final AppealRepository appealRepository;
  private final ModerationCaseRepository caseRepository;
  private final ModerationDecisionRepository decisionRepository;
  private final ModerationTargetLookup targetLookup;
  private final ModerationEffectApplier effectApplier;
  private final Clock clock;

  public AppealService(
      AppealRepository appealRepository,
      ModerationCaseRepository caseRepository,
      ModerationDecisionRepository decisionRepository,
      ModerationTargetLookup targetLookup,
      ModerationEffectApplier effectApplier,
      Clock clock) {
    this.appealRepository = Objects.requireNonNull(appealRepository, "appealRepository");
    this.caseRepository = Objects.requireNonNull(caseRepository, "caseRepository");
    this.decisionRepository = Objects.requireNonNull(decisionRepository, "decisionRepository");
    this.targetLookup = Objects.requireNonNull(targetLookup, "targetLookup");
    this.effectApplier = Objects.requireNonNull(effectApplier, "effectApplier");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  /**
   * Files an appeal against the latest adverse decision on the author's content.
   *
   * <p>The appellant names the content, not a decision id: an author knows their review was taken
   * down, and should not have to be handed an internal identifier to say so.
   *
   * @throws NothingToAppealException if no adverse decision was made on this content
   * @throws NotTheAffectedAuthorException if the caller did not write the content
   * @throws AppealAlreadyFiledException if that decision was already appealed
   */
  public Appeal file(AppellantId appellantId, ModerationTargetRef target, String appealText) {
    Objects.requireNonNull(appellantId, "appellantId");
    Objects.requireNonNull(target, "target");

    // Deliberately no restriction check here, unlike reviews and reports. An appeal is how somebody
    // challenges a decision made against them, and restricting an account is frequently part of
    // that same decision — refusing appeals from restricted accounts would mean a takedown could
    // remove the route to contest it, which is the remedy P-014 exists to protect.

    ModeratableTarget content =
        targetLookup.find(target).orElseThrow(() -> new ModerationTargetNotFoundException(target));
    if (!content.authorAccountId().equals(appellantId.value())) {
      throw new NotTheAffectedAuthorException(
          "only the author a decision was made against may appeal it");
    }

    ModerationDecision decision = latestAdverseDecisionOn(target);
    if (appealRepository.findByDecision(decision.id()).isPresent()) {
      throw new AppealAlreadyFiledException("this decision has already been appealed");
    }

    Appeal appeal =
        Appeal.file(AppealId.of(UUID.randomUUID()), decision, appellantId, appealText, clock);
    appealRepository.create(appeal);
    markCaseAppealed(decision);
    return appeal;
  }

  /** The appellant's own appeal; anyone else's is reported as missing. */
  public Appeal findOwn(AppealId appealId, AppellantId appellantId) {
    Objects.requireNonNull(appealId, "appealId");
    Objects.requireNonNull(appellantId, "appellantId");
    return appealRepository
        .findById(appealId)
        .filter(appeal -> appeal.appellantId().equals(appellantId))
        .orElseThrow(() -> new AppealNotFoundException(appealId));
  }

  public List<PendingAppeal> pending() {
    return appealRepository.findPending().stream().map(this::withWhatIsBeingContested).toList();
  }

  /**
   * Joins an appeal to the decision it challenges and the content both concern.
   *
   * <p>An appeal whose decision or case has gone missing is a broken record, not a decidable
   * appeal; failing loudly beats handing a moderator a row with blanks where the reason for the
   * takedown should be. {@link #decisionOf} already answers the missing-decision half, and it
   * answers it the same way here as it does for {@link #overturn} — one condition, one story.
   */
  private PendingAppeal withWhatIsBeingContested(Appeal appeal) {
    ModerationDecision decision = decisionOf(appeal);
    ModerationCase moderationCase =
        caseRepository
            .findById(decision.caseId())
            .orElseThrow(() -> new ModerationCaseNotFoundException(decision.caseId()));
    return new PendingAppeal(appeal, decision, moderationCase.target());
  }

  /** Confirms the original decision. The content stays as it is. */
  public Appeal uphold(AppealId appealId, ModeratorId moderatorId, String explanation) {
    Appeal appeal = require(appealId);
    appeal.uphold(moderatorId, explanation, clock);
    appealRepository.save(appeal);
    return appeal;
  }

  /**
   * Reverses the original decision and puts the content back.
   *
   * <p>The content is restored before the outcome is recorded, for the same reason a decision is
   * applied before it is recorded (chunk 4): if the write fails afterwards, the author has their
   * review back and the appeal can be decided again, which is the better of the two failures.
   *
   * @throws AppealDeciderConflictException if the moderator being appealed against tries to hear it
   * @throws ModerationEffectConflictException if the content could not be put back
   */
  public Appeal overturn(AppealId appealId, ModeratorId moderatorId, String explanation) {
    Appeal appeal = require(appealId);
    if (!appeal.canBeDecidedBy(moderatorId)) {
      // Checked before the effect, because reinstating content cannot be undone by throwing.
      throw new AppealDeciderConflictException(
          "an appeal must be decided by someone other than the moderator who decided the case");
    }

    ModerationDecision appealed = decisionOf(appeal);
    caseRepository
        .findById(appealed.caseId())
        .ifPresent(
            moderationCase ->
                effectApplier.reverse(
                    moderationCase.target(),
                    appealed.action(),
                    appealed.affectedTargetVersion().orElse(0L),
                    moderatorId,
                    APPEAL_OVERTURNED));

    appeal.overturn(moderatorId, explanation, clock);
    appealRepository.save(appeal);
    return appeal;
  }

  private ModerationDecision latestAdverseDecisionOn(ModerationTargetRef target) {
    ModerationCase moderationCase =
        caseRepository
            .findLiveByTarget(target)
            .orElseThrow(
                () -> new NothingToAppealException("no decision has been made about this content"));
    return decisionRepository.findByCase(moderationCase.id()).stream()
        .filter(decision -> decision.action().requiresPublicExplanation())
        .max(Comparator.comparing(ModerationDecision::decidedAt))
        .orElseThrow(
            () ->
                new NothingToAppealException(
                    "nothing was decided about this content that took anything away"));
  }

  private void markCaseAppealed(ModerationDecision decision) {
    Optional<ModerationCase> moderationCase = caseRepository.findById(decision.caseId());
    moderationCase.ifPresent(
        found -> {
          found.markAppealed(clock);
          caseRepository.save(found);
        });
  }

  private ModerationDecision decisionOf(Appeal appeal) {
    return decisionRepository
        .findById(appeal.decisionId())
        .orElseThrow(
            () -> new NothingToAppealException("the decision this appeal refers to is missing"));
  }

  private Appeal require(AppealId appealId) {
    Objects.requireNonNull(appealId, "appealId");
    return appealRepository
        .findById(appealId)
        .orElseThrow(() -> new AppealNotFoundException(appealId));
  }
}

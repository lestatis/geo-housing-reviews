package com.example.geohousing.moderation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.geohousing.identity.api.AccountStanding;
import com.example.geohousing.moderation.domain.Appeal;
import com.example.geohousing.moderation.domain.AppealDeciderConflictException;
import com.example.geohousing.moderation.domain.AppealStatus;
import com.example.geohousing.moderation.domain.AppellantId;
import com.example.geohousing.moderation.domain.DecisionAction;
import com.example.geohousing.moderation.domain.ModerationCaseId;
import com.example.geohousing.moderation.domain.ModerationTargetRef;
import com.example.geohousing.moderation.domain.ModeratorId;
import com.example.geohousing.moderation.domain.PolicyVersion;
import com.example.geohousing.moderation.domain.ReasonCode;
import com.example.geohousing.moderation.domain.ReportCategory;
import com.example.geohousing.moderation.domain.ReporterId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AppealServiceTest {

  /** Nobody is restricted unless a test says so. */
  private static final AccountStanding UNRESTRICTED = accountId -> false;

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-07-30T10:00:00Z"), ZoneOffset.UTC);

  private final InMemoryReportRepository reports = new InMemoryReportRepository();
  private final InMemoryModerationCaseRepository cases = new InMemoryModerationCaseRepository();
  private final InMemoryModerationDecisionRepository decisions =
      new InMemoryModerationDecisionRepository();
  private final InMemoryModerationTargetLookup targets = new InMemoryModerationTargetLookup();
  private final InMemoryModerationEffectApplier effects = new InMemoryModerationEffectApplier();
  private final InMemoryAppealRepository appeals = new InMemoryAppealRepository();

  private final ReportIntakeService intake =
      new ReportIntakeService(reports, cases, targets, UNRESTRICTED, CLOCK);
  private final ModerationCaseService caseService =
      new ModerationCaseService(
          cases, decisions, reports, targets, effects, PolicyVersion.of(1), CLOCK);
  private final AppealService appealService =
      new AppealService(appeals, cases, decisions, targets, effects, CLOCK);

  private UUID author;
  private ModerationTargetRef target;
  private ModeratorId original;
  private ModerationCaseId decidedCaseId;

  @Test
  void anAuthorCanAppealADecisionThatTookTheirContentDown() {
    removedReview();

    Appeal appeal = appealService.file(appellant(), target, "The flat number was my own.");

    assertThat(appeal.status()).isEqualTo(AppealStatus.PENDING);
    assertThat(appeal.originalDecider()).isEqualTo(original);
    assertThat(appeals.findByDecision(appeal.decisionId())).isPresent();
  }

  @Test
  void onlyTheAffectedAuthorMayAppeal() {
    removedReview();

    assertThatThrownBy(
            () -> appealService.file(AppellantId.of(UUID.randomUUID()), target, "Me too."))
        .isInstanceOf(NotTheAffectedAuthorException.class);
  }

  @Test
  void aDecisionIsAppealedOnce() {
    removedReview();
    appealService.file(appellant(), target, "First.");

    assertThatThrownBy(() -> appealService.file(appellant(), target, "Again."))
        .isInstanceOf(AppealAlreadyFiledException.class);
  }

  @Test
  void thereIsNothingToAppealWhenTheDecisionTookNothingAway() {
    // An approval is not something its beneficiary appeals.
    target = targets.givenReviewBy(UUID.randomUUID(), 1L);
    author = targets.find(target).orElseThrow().authorAccountId();
    ModerationCaseId caseId = reportedCase();
    original = ModeratorId.of(UUID.randomUUID());
    caseService.assign(caseId, original);
    caseService.decide(
        caseId, original, DecisionAction.APPROVE, ReasonCode.of("CLEAN"), null, null);

    assertThatThrownBy(() -> appealService.file(appellant(), target, "I still object."))
        .isInstanceOf(NothingToAppealException.class);
  }

  @Test
  void overturningPutsTheContentBack() {
    removedReview();
    Appeal appeal = appealService.file(appellant(), target, "Nothing identifies anyone.");
    effects.applied.clear();

    Appeal decided =
        appealService.overturn(appeal.id(), ModeratorId.of(UUID.randomUUID()), "Nobody was named.");

    assertThat(decided.status()).isEqualTo(AppealStatus.OVERTURNED);
    // The remedy is the point: recording that the takedown was wrong without undoing it would
    // leave the takedown having worked anyway.
    assertThat(effects.reversed)
        .singleElement()
        .satisfies(r -> assertThat(r.action()).isEqualTo(DecisionAction.REMOVE));
  }

  @Test
  void upholdingLeavesTheContentWhereItIs() {
    removedReview();
    Appeal appeal = appealService.file(appellant(), target, "Please reconsider.");
    effects.applied.clear();

    Appeal decided =
        appealService.uphold(
            appeal.id(), ModeratorId.of(UUID.randomUUID()), "It named a neighbour.");

    assertThat(decided.status()).isEqualTo(AppealStatus.UPHELD);
    assertThat(effects.reversed).isEmpty();
  }

  @Test
  void theModeratorBeingAppealedAgainstCannotHearIt() {
    removedReview();
    Appeal appeal = appealService.file(appellant(), target, "Please reconsider.");

    assertThatThrownBy(() -> appealService.overturn(appeal.id(), original, "I was wrong."))
        .isInstanceOf(AppealDeciderConflictException.class);

    // Refused before the content moved: reinstating cannot be undone by throwing afterwards.
    assertThat(effects.reversed).isEmpty();
    assertThat(appeals.findById(appeal.id()).orElseThrow().status())
        .isEqualTo(AppealStatus.PENDING);
  }

  @Test
  void contentThatCannotBeputBackLeavesTheAppealUndecided() {
    removedReview();
    Appeal appeal = appealService.file(appellant(), target, "Please restore it.");
    // The author started a fresh review in the meantime, and the one-live-review rule will not
    // hold two — so the owning module refuses to take the old one back.
    effects.refuseReverse = true;

    assertThatThrownBy(
            () ->
                appealService.overturn(
                    appeal.id(), ModeratorId.of(UUID.randomUUID()), "Restoring it."))
        .isInstanceOf(ModerationEffectConflictException.class);

    // Not marked overturned on content that is still gone: the moderator is told, and can uphold
    // with an explanation instead.
    assertThat(appeals.findById(appeal.id()).orElseThrow().status())
        .isEqualTo(AppealStatus.PENDING);
  }

  @Test
  void anAppealBelongsToItsAppellantAlone() {
    removedReview();
    Appeal appeal = appealService.file(appellant(), target, "Mine.");

    assertThat(appealService.findOwn(appeal.id(), appellant()).id()).isEqualTo(appeal.id());
    assertThatThrownBy(() -> appealService.findOwn(appeal.id(), AppellantId.of(UUID.randomUUID())))
        .isInstanceOf(AppealNotFoundException.class);
  }

  @Test
  void thePendingQueueHoldsOnlyUndecidedAppeals() {
    removedReview();
    Appeal appeal = appealService.file(appellant(), target, "Mine.");
    assertThat(appealService.pending()).hasSize(1);

    appealService.uphold(appeal.id(), ModeratorId.of(UUID.randomUUID()), "It stands.");

    assertThat(appealService.pending()).isEmpty();
  }

  @Test
  void aPendingAppealCarriesTheDecisionItContests() {
    // A moderator cannot hear an appeal against a decision they cannot see. The appellant's text
    // says why they disagree; without what was decided, and on which case, that is one half of an
    // argument and the queue is a request to guess.
    removedReview();
    appealService.file(appellant(), target, "I never named anyone.");

    PendingAppeal pending = appealService.pending().getFirst();

    assertThat(pending.appeal().appealText()).isEqualTo("I never named anyone.");
    assertThat(pending.contestedDecision().action()).isEqualTo(DecisionAction.REMOVE);
    assertThat(pending.contestedDecision().reasonCode()).isEqualTo(ReasonCode.of("DOXXING"));
    assertThat(pending.contestedDecision().publicExplanation()).contains("Named a neighbour.");
    assertThat(pending.contestedDecision().caseId()).isEqualTo(decidedCaseId);
  }

  @Test
  void aPendingAppealNamesTheContentUnderDispute() {
    // The case id alone would make a moderator open a second screen to learn whether this is even
    // about a review; the target is what the appeal is ultimately about.
    removedReview();
    appealService.file(appellant(), target, "I never named anyone.");

    assertThat(appealService.pending().getFirst().target()).isEqualTo(target);
  }

  private void removedReview() {
    target = targets.givenReviewBy(UUID.randomUUID(), 1L);
    author = targets.find(target).orElseThrow().authorAccountId();
    decidedCaseId = reportedCase();
    ModerationCaseId caseId = decidedCaseId;
    original = ModeratorId.of(UUID.randomUUID());
    caseService.assign(caseId, original);
    caseService.decide(
        caseId,
        original,
        DecisionAction.REMOVE,
        ReasonCode.of("DOXXING"),
        "Named a neighbour.",
        null);
  }

  private ModerationCaseId reportedCase() {
    return intake
        .file(ReporterId.of(UUID.randomUUID()), target, ReportCategory.PERSONAL_DATA, null)
        .caseId()
        .orElseThrow();
  }

  private AppellantId appellant() {
    return AppellantId.of(author);
  }

  @Test
  void overturningLiftsTheRestrictionThatDecisionPlaced() {
    // Winning an appeal and staying barred from contributing is half a reversal. The account was
    // restricted by this decision, so overturning it must end that restriction.
    UUID placed = UUID.randomUUID();
    effects.restrictionToReport = placed;
    restrictedAuthor();
    Appeal appeal = appealService.file(appellant(), target, "Nothing identifies anyone.");

    appealService.overturn(appeal.id(), ModeratorId.of(UUID.randomUUID()), "Nobody was named.");

    assertThat(effects.liftedRestrictions)
        .as("the appeal was won and the account is still restricted")
        .containsExactly(placed);
  }

  @Test
  void overturningLeavesARestrictionThisDecisionDidNotPlace() {
    // The test the review asked for by name. A decision that restricted nobody — because the
    // account was already restricted by an unrelated case — owns nothing to lift. Lifting "the
    // account's active restriction" would free somebody a second moderator never reconsidered.
    effects.restrictionToReport = null;
    restrictedAuthor();
    Appeal appeal = appealService.file(appellant(), target, "Nothing identifies anyone.");

    appealService.overturn(appeal.id(), ModeratorId.of(UUID.randomUUID()), "Nobody was named.");

    assertThat(effects.liftedRestrictions)
        .as("an unrelated case's restriction was lifted by an appeal against a different decision")
        .isEmpty();
  }

  private void restrictedAuthor() {
    target = targets.givenReviewBy(UUID.randomUUID(), 1L);
    author = targets.find(target).orElseThrow().authorAccountId();
    decidedCaseId = reportedCase();
    original = ModeratorId.of(UUID.randomUUID());
    caseService.assign(decidedCaseId, original);
    caseService.decide(
        decidedCaseId,
        original,
        DecisionAction.RESTRICT_ACCOUNT,
        ReasonCode.of("HARASSMENT"),
        "Your repeated reports targeted another resident.",
        null);
  }
}

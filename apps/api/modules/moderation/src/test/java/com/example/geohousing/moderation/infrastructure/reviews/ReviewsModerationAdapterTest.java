package com.example.geohousing.moderation.infrastructure.reviews;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.geohousing.identity.api.AccountRestraint;
import com.example.geohousing.moderation.application.ModeratableTarget;
import com.example.geohousing.moderation.application.ModerationEffectConflictException;
import com.example.geohousing.moderation.domain.DecisionAction;
import com.example.geohousing.moderation.domain.ModerationTargetRef;
import com.example.geohousing.moderation.domain.ModerationTargetType;
import com.example.geohousing.moderation.domain.ModeratorId;
import com.example.geohousing.moderation.domain.ReasonCode;
import com.example.geohousing.reviews.api.ModeratableReview;
import com.example.geohousing.reviews.api.ReviewModerationConflictException;
import com.example.geohousing.reviews.api.ReviewModerationEffect;
import com.example.geohousing.reviews.api.ReviewModerationGateway;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReviewsModerationAdapterTest {

  /** These tests are about content effects; restricting an author has its own coverage. */
  private static final AccountRestraint NO_RESTRAINT =
      new AccountRestraint() {
        @Override
        public java.util.Optional<UUID> restrict(
            UUID accountId, UUID moderatorAccountId, String reason) {
          return java.util.Optional.empty();
        }

        @Override
        public void lift(UUID restrictionId, UUID moderatorAccountId) {
          // Nothing was restricted, so there is nothing to lift.
        }
      };

  private final FakeReviewModerationGateway gateway = new FakeReviewModerationGateway();
  private final ReviewsModerationTargetLookup lookup = new ReviewsModerationTargetLookup(gateway);
  private final ReviewsModerationEffectApplier applier =
      new ReviewsModerationEffectApplier(gateway, NO_RESTRAINT);

  @Test
  void itTranslatesWhatReviewsKnowsIntoWhatModerationNeeds() {
    UUID reviewId = UUID.randomUUID();
    UUID author = UUID.randomUUID();
    gateway.reviews.put(reviewId, new ModeratableReview(reviewId, author, 6L, true));
    ModerationTargetRef ref = ModerationTargetRef.review(reviewId);

    ModeratableTarget target = lookup.find(ref).orElseThrow();

    assertThat(target.ref()).isEqualTo(ref);
    assertThat(target.authorAccountId()).isEqualTo(author);
    assertThat(target.version()).isEqualTo(6L);
  }

  @Test
  void aReviewThatDoesNotExistIsAbsentRatherThanAnError() {
    assertThat(lookup.find(ModerationTargetRef.review(UUID.randomUUID()))).isEmpty();
  }

  @Test
  void eachActionWithAContentEffectReachesTheRightOne() {
    assertThat(applyAndCapture(DecisionAction.REJECT)).contains(ReviewModerationEffect.REJECT);
    assertThat(applyAndCapture(DecisionAction.HIDE)).contains(ReviewModerationEffect.HIDE);
    assertThat(applyAndCapture(DecisionAction.REMOVE)).contains(ReviewModerationEffect.REMOVE);
  }

  @Test
  void approvingContentAwaitingModerationPublishesIt() {
    // The pre-moderation queue clearing: the review is not yet visible, so approval makes it so.
    UUID reviewId = registerReview(false);

    applyTo(reviewId, DecisionAction.APPROVE);

    assertThat(gateway.calls)
        .extracting(Call::effect)
        .containsExactly(ReviewModerationEffect.PUBLISH);
  }

  @Test
  void approvingContentThatIsAlreadyVisibleChangesNothing() {
    // A report was heard and not upheld. The review was never withdrawn, so there is nothing to
    // restore — and an unconditional publish would throw, which is the common case, not the rare
    // one.
    UUID reviewId = registerReview(true);

    applyTo(reviewId, DecisionAction.APPROVE);

    assertThat(gateway.calls).isEmpty();
  }

  @Test
  void actionsWithNoContentEffectTouchNothingButAreNotErrors() {
    // Each is still a recorded decision with its reason; it just is not a change reviews can make.
    for (DecisionAction action :
        List.of(
            DecisionAction.APPROVE_WITH_REDACTION,
            DecisionAction.REQUEST_CHANGES,
            DecisionAction.ESCALATE)) {
      assertThat(applyAndCapture(action)).as("%s", action).isEmpty();
    }
  }

  @Test
  void restrictingAnAccountStopsTheAuthorRatherThanTheContent() {
    // Until this existed, RESTRICT_ACCOUNT was a decision that recorded itself and did nothing: the
    // author was told they had been restricted and carried on posting.
    RecordingRestraint restraint = new RecordingRestraint();
    ReviewsModerationEffectApplier applier = new ReviewsModerationEffectApplier(gateway, restraint);
    UUID reviewId = UUID.randomUUID();
    UUID author = UUID.randomUUID();
    gateway.reviews.put(reviewId, new ModeratableReview(reviewId, author, 1L, true));
    ModeratorId moderator = ModeratorId.of(UUID.randomUUID());

    applier.apply(
        ModerationTargetRef.review(reviewId),
        DecisionAction.RESTRICT_ACCOUNT,
        1L,
        moderator,
        ReasonCode.of("HARASSMENT"));

    assertThat(restraint.restricted).containsExactly(author);
    assertThat(restraint.reasons).containsExactly("HARASSMENT");
    // The review itself is untouched — restricting the person is not withdrawing what they wrote.
    assertThat(gateway.calls).isEmpty();
  }

  @Test
  void restrictingTheAuthorOfContentThatIsGoneIsAConflict() {
    // The decision said somebody should be stopped. Quietly restricting nobody would report success
    // for an outcome that did not happen.
    ReviewsModerationEffectApplier applier =
        new ReviewsModerationEffectApplier(gateway, new RecordingRestraint());

    assertThatThrownBy(
            () ->
                applier.apply(
                    ModerationTargetRef.review(UUID.randomUUID()),
                    DecisionAction.RESTRICT_ACCOUNT,
                    1L,
                    ModeratorId.of(UUID.randomUUID()),
                    ReasonCode.of("HARASSMENT")))
        .isInstanceOf(ModerationEffectConflictException.class);
  }

  @Test
  void overturningLiftsTheSpecificRestrictionTheDecisionCreated() {
    RecordingRestraint restraint = new RecordingRestraint();
    ReviewsModerationEffectApplier applier = new ReviewsModerationEffectApplier(gateway, restraint);
    UUID reviewId = UUID.randomUUID();
    UUID author = UUID.randomUUID();
    gateway.reviews.put(reviewId, new ModeratableReview(reviewId, author, 1L, true));
    ModeratorId originalModerator = ModeratorId.of(UUID.randomUUID());

    UUID placed =
        applier
            .apply(
                ModerationTargetRef.review(reviewId),
                DecisionAction.RESTRICT_ACCOUNT,
                1L,
                originalModerator,
                ReasonCode.of("HARASSMENT"))
            .orElseThrow();
    // Deleting the review while the appeal waits must not strand the restriction. Identity owns
    // the restriction's account relationship, so moderation does not need the vanished review.
    gateway.reviews.remove(reviewId);
    applier.reverse(
        ModerationTargetRef.review(reviewId),
        DecisionAction.RESTRICT_ACCOUNT,
        1L,
        ModeratorId.of(UUID.randomUUID()),
        ReasonCode.of("APPEAL_OVERTURNED"),
        placed);

    assertThat(restraint.lifted).containsExactly(placed);
    // A restriction does not withdraw a review, so there is no content effect to reverse.
    assertThat(gateway.calls).isEmpty();
  }

  private static final class RecordingRestraint implements AccountRestraint {

    private final List<UUID> restricted = new java.util.ArrayList<>();
    private final List<String> reasons = new java.util.ArrayList<>();
    private final List<UUID> lifted = new java.util.ArrayList<>();

    /** The identifier a real identity would mint, so the caller has something to record. */
    private UUID placed;

    @Override
    public java.util.Optional<UUID> restrict(
        UUID accountId, UUID moderatorAccountId, String reason) {
      restricted.add(accountId);
      reasons.add(reason);
      placed = UUID.randomUUID();
      return java.util.Optional.of(placed);
    }

    @Override
    public void lift(UUID restrictionId, UUID moderatorAccountId) {
      lifted.add(restrictionId);
    }
  }

  @Test
  void theVersionTheModeratorJudgedIsCarriedThrough() {
    applier.apply(
        ModerationTargetRef.review(UUID.randomUUID()),
        DecisionAction.REMOVE,
        9L,
        ModeratorId.of(UUID.randomUUID()),
        ReasonCode.of("DOXXING"));

    assertThat(gateway.calls)
        .singleElement()
        .satisfies(call -> assertThat(call.version()).isEqualTo(9L));
  }

  @Test
  void aConflictFromReviewsBecomesThisModulesConflict() {
    gateway.conflict = true;

    // The application layer should not have to know which module refused, only that the content
    // moved under the moderator.
    assertThatThrownBy(
            () ->
                applier.apply(
                    ModerationTargetRef.review(UUID.randomUUID()),
                    DecisionAction.REMOVE,
                    1L,
                    ModeratorId.of(UUID.randomUUID()),
                    ReasonCode.of("DOXXING")))
        .isInstanceOf(ModerationEffectConflictException.class);
  }

  @Test
  void reviewsAreStillTheOnlyModeratableContent() {
    // These adapters refuse any other target type rather than returning empty, because a silent
    // empty would read as "content does not exist" and quietly drop real reports. That refusal is
    // unreachable while REVIEW is the only constant — so this pins the assumption instead: adding
    // a target type fails here, which is the prompt to wire an adapter for it.
    assertThat(ModerationTargetType.values()).containsExactly(ModerationTargetType.REVIEW);
  }

  @Test
  void aReviewTargetIsAlwaysAccepted() {
    assertThatCode(
            () ->
                applier.apply(
                    ModerationTargetRef.review(UUID.randomUUID()),
                    DecisionAction.HIDE,
                    1L,
                    ModeratorId.of(UUID.randomUUID()),
                    ReasonCode.of("PRIVACY_RISK")))
        .doesNotThrowAnyException();
  }

  private UUID registerReview(boolean published) {
    UUID reviewId = UUID.randomUUID();
    gateway.reviews.put(
        reviewId, new ModeratableReview(reviewId, UUID.randomUUID(), 1L, published));
    gateway.calls.clear();
    return reviewId;
  }

  private void applyTo(UUID reviewId, DecisionAction action) {
    applier.apply(
        ModerationTargetRef.review(reviewId),
        action,
        1L,
        ModeratorId.of(UUID.randomUUID()),
        ReasonCode.of("REASON"));
  }

  private Optional<ReviewModerationEffect> applyAndCapture(DecisionAction action) {
    gateway.calls.clear();
    applier.apply(
        ModerationTargetRef.review(UUID.randomUUID()),
        action,
        1L,
        ModeratorId.of(UUID.randomUUID()),
        ReasonCode.of("REASON"));
    return gateway.calls.stream().map(Call::effect).findFirst();
  }

  private record Call(UUID reviewId, ReviewModerationEffect effect, long version) {}

  private static final class FakeReviewModerationGateway implements ReviewModerationGateway {

    private final java.util.Map<UUID, ModeratableReview> reviews = new java.util.HashMap<>();
    private final List<Call> calls = new ArrayList<>();
    private boolean conflict;

    @Override
    public Optional<ModeratableReview> find(UUID reviewId) {
      return Optional.ofNullable(reviews.get(reviewId));
    }

    @Override
    public boolean apply(
        UUID reviewId,
        ReviewModerationEffect effect,
        long expectedVersion,
        UUID moderatorAccountId,
        String reasonCode) {
      if (conflict) {
        throw new ReviewModerationConflictException("changed");
      }
      calls.add(new Call(reviewId, effect, expectedVersion));
      return true;
    }
  }
}

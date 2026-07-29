package com.example.geohousing.moderation.infrastructure.reviews;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

  private final FakeReviewModerationGateway gateway = new FakeReviewModerationGateway();
  private final ReviewsModerationTargetLookup lookup = new ReviewsModerationTargetLookup(gateway);
  private final ReviewsModerationEffectApplier applier =
      new ReviewsModerationEffectApplier(gateway);

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
    assertThat(applyAndCapture(DecisionAction.APPROVE)).contains(ReviewModerationEffect.PUBLISH);
    assertThat(applyAndCapture(DecisionAction.REJECT)).contains(ReviewModerationEffect.REJECT);
    assertThat(applyAndCapture(DecisionAction.HIDE)).contains(ReviewModerationEffect.HIDE);
    assertThat(applyAndCapture(DecisionAction.REMOVE)).contains(ReviewModerationEffect.REMOVE);
  }

  @Test
  void actionsWithNoContentEffectTouchNothingButAreNotErrors() {
    // Each is still a recorded decision with its reason; it just is not a change reviews can make.
    for (DecisionAction action :
        List.of(
            DecisionAction.APPROVE_WITH_REDACTION,
            DecisionAction.REQUEST_CHANGES,
            DecisionAction.RESTRICT_ACCOUNT,
            DecisionAction.ESCALATE)) {
      assertThat(applyAndCapture(action)).as("%s", action).isEmpty();
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

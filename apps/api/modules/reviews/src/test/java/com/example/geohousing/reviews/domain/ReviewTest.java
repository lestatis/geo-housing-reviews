package com.example.geohousing.reviews.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReviewTest {

  private static final Clock CREATED =
      Clock.fixed(Instant.parse("2026-07-20T10:00:00Z"), ZoneOffset.UTC);
  private static final Clock LATER =
      Clock.fixed(Instant.parse("2026-07-21T10:00:00Z"), ZoneOffset.UTC);
  private static final Clock EVEN_LATER =
      Clock.fixed(Instant.parse("2026-07-22T10:00:00Z"), ZoneOffset.UTC);

  private static Review draft() {
    return Review.create(
        ReviewId.of(UUID.randomUUID()),
        PropertyRef.of(UUID.randomUUID()),
        AuthorId.of(UUID.randomUUID()),
        RelationshipType.CURRENT_RESIDENT,
        null,
        CREATED);
  }

  private static Review draftWithContent() {
    Review review = draft();
    review.appendVersion(
        "ka",
        "კარგი შენობა, კარგი მეზობლები",
        "quiet",
        "old lift",
        Recommendation.RECOMMEND,
        List.of(CategoryRating.rated("NOISE", 4, null)),
        null,
        CREATED);
    return review;
  }

  @Test
  void createsAnEmptyDraftAsUnverified() {
    Review review = draft();

    assertThat(review.status()).isEqualTo(ReviewStatus.DRAFT);
    assertThat(review.verificationTier()).isEqualTo(VerificationTier.UNVERIFIED);
    assertThat(review.versions()).isEmpty();
    assertThat(review.currentVersion()).isEmpty();
    assertThat(review.publishedAt()).isEmpty();
    assertThat(review.version()).isZero();
  }

  @Test
  void theFirstVersionNeedsNoEditReasonButLaterOnesDo() {
    Review review = draftWithContent();
    assertThat(review.currentVersion().orElseThrow().versionNumber()).isEqualTo(1);
    assertThat(review.currentVersion().orElseThrow().editReason()).isNull();

    assertThatThrownBy(
            () ->
                review.appendVersion(
                    "ka", "განახლებული", null, null, Recommendation.NEUTRAL, List.of(), " ", LATER))
        .isInstanceOf(IllegalArgumentException.class);

    review.appendVersion(
        "ka", "განახლებული", null, null, Recommendation.NEUTRAL, List.of(), "fixed typos", LATER);
    assertThat(review.currentVersion().orElseThrow().versionNumber()).isEqualTo(2);
    assertThat(review.currentVersion().orElseThrow().editReason()).isEqualTo("fixed typos");
  }

  @Test
  void anEmptyDraftCannotBeSubmitted() {
    assertThatThrownBy(() -> draft().submit(LATER))
        .isInstanceOf(IllegalReviewStateTransitionException.class);
  }

  @Test
  void submitPublishFlowSetsPublishedAtOnce() {
    Review review = draftWithContent();
    review.submit(LATER);
    assertThat(review.status()).isEqualTo(ReviewStatus.PENDING_MODERATION);

    review.publish(LATER);
    assertThat(review.status()).isEqualTo(ReviewStatus.PUBLISHED);
    assertThat(review.publishedAt()).contains(LATER.instant());
  }

  @Test
  void aDraftCannotBePublishedDirectly() {
    assertThatThrownBy(() -> draftWithContent().publish(LATER))
        .isInstanceOf(IllegalReviewStateTransitionException.class);
  }

  @Test
  void editingAPublishedReviewSendsItBackToModerationAndKeepsFirstPublication() {
    Review review = draftWithContent();
    review.submit(LATER);
    review.publish(LATER);

    review.appendVersion(
        "ka",
        "დამატებული დეტალები",
        null,
        null,
        Recommendation.RECOMMEND,
        List.of(),
        "added details",
        EVEN_LATER);
    assertThat(review.status()).isEqualTo(ReviewStatus.PENDING_MODERATION);

    review.publish(EVEN_LATER);
    // The first publication time is preserved across the re-moderation cycle.
    assertThat(review.publishedAt()).contains(LATER.instant());
  }

  @Test
  void hideAndRestoreToggleAPublishedReview() {
    Review review = draftWithContent();
    review.submit(LATER);
    review.publish(LATER);

    review.hide(EVEN_LATER);
    assertThat(review.status()).isEqualTo(ReviewStatus.HIDDEN);

    review.restore(EVEN_LATER);
    assertThat(review.status()).isEqualTo(ReviewStatus.PUBLISHED);
    assertThat(review.publishedAt()).contains(LATER.instant());
  }

  @Test
  void onlyAPendingReviewCanBeRejected() {
    Review review = draftWithContent();
    assertThatThrownBy(() -> review.reject(LATER))
        .isInstanceOf(IllegalReviewStateTransitionException.class);

    review.submit(LATER);
    review.reject(LATER);
    assertThat(review.status()).isEqualTo(ReviewStatus.REJECTED);
    assertThat(review.isTerminal()).isTrue();
  }

  @Test
  void terminalReviewsRejectAllFurtherMutation() {
    Review review = draftWithContent();
    review.submit(LATER);
    review.reject(LATER);

    assertThatThrownBy(
            () ->
                review.appendVersion(
                    "ka", "x", null, null, Recommendation.NEUTRAL, List.of(), "why", LATER))
        .isInstanceOf(IllegalReviewStateTransitionException.class);
    assertThatThrownBy(() -> review.remove(LATER))
        .isInstanceOf(IllegalReviewStateTransitionException.class);
    assertThatThrownBy(
            () -> review.updateVerificationTier(VerificationTier.DOCUMENT_VERIFIED, LATER))
        .isInstanceOf(IllegalReviewStateTransitionException.class);
  }

  @Test
  void aReviewCanBeRemovedFromAnyLiveState() {
    Review published = draftWithContent();
    published.submit(LATER);
    published.publish(LATER);
    published.remove(EVEN_LATER);
    assertThat(published.status()).isEqualTo(ReviewStatus.REMOVED);

    Review stillDraft = draft();
    stillDraft.remove(LATER);
    assertThat(stillDraft.status()).isEqualTo(ReviewStatus.REMOVED);
  }

  @Test
  void theVerificationTierIsAProjectionThatCanChange() {
    Review review = draftWithContent();
    review.updateVerificationTier(VerificationTier.RELATIONSHIP_SIGNAL, LATER);
    assertThat(review.verificationTier()).isEqualTo(VerificationTier.RELATIONSHIP_SIGNAL);
  }

  @Test
  void reconstituteRejectsAPublishedReviewWithoutAPublicationTime() {
    assertThatThrownBy(
            () ->
                Review.reconstitute(
                    ReviewId.of(UUID.randomUUID()),
                    PropertyRef.of(UUID.randomUUID()),
                    AuthorId.of(UUID.randomUUID()),
                    RelationshipType.OWNER,
                    null,
                    ReviewStatus.PUBLISHED,
                    List.of(),
                    VerificationTier.UNVERIFIED,
                    null,
                    CREATED.instant(),
                    CREATED.instant(),
                    0L))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void reconstituteRejectsNonSequentialVersionNumbers() {
    ReviewVersion second =
        new ReviewVersion(
            UUID.randomUUID(),
            2,
            "en",
            "content",
            null,
            null,
            Recommendation.NEUTRAL,
            List.of(),
            "reason",
            CREATED.instant());

    assertThatThrownBy(
            () ->
                Review.reconstitute(
                    ReviewId.of(UUID.randomUUID()),
                    PropertyRef.of(UUID.randomUUID()),
                    AuthorId.of(UUID.randomUUID()),
                    RelationshipType.OWNER,
                    null,
                    ReviewStatus.DRAFT,
                    List.of(second),
                    VerificationTier.UNVERIFIED,
                    null,
                    CREATED.instant(),
                    CREATED.instant(),
                    0L))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void versionsListIsAnUnmodifiableCopy() {
    Review review = draftWithContent();
    assertThatThrownBy(() -> review.versions().clear())
        .isInstanceOf(UnsupportedOperationException.class);
  }
}

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

  /**
   * What a repository does between two units of work: rebuilds the aggregate from its persisted
   * state. Editing always starts from a reloaded instance — an aggregate appends at most one
   * version per load.
   */
  private static Review reloaded(Review review) {
    return Review.reconstitute(
        review.id(),
        review.propertyRef(),
        review.authorId(),
        review.relationshipType(),
        review.residencePeriod().orElse(null),
        review.status(),
        review.currentVersion().orElse(null),
        review.verificationTier(),
        review.publishedAt().orElse(null),
        review.createdAt(),
        review.updatedAt(),
        review.version());
  }

  private static void append(Review review, String body, String editReason, Clock clock) {
    review.appendVersion(
        "ka", body, null, null, Recommendation.NEUTRAL, List.of(), editReason, clock);
  }

  @Test
  void createsAnEmptyDraftAsUnverified() {
    Review review = draft();

    assertThat(review.status()).isEqualTo(ReviewStatus.DRAFT);
    assertThat(review.verificationTier()).isEqualTo(VerificationTier.UNVERIFIED);
    assertThat(review.currentVersion()).isEmpty();
    assertThat(review.publishedAt()).isEmpty();
    assertThat(review.version()).isZero();
  }

  @Test
  void theFirstVersionNeedsNoEditReasonButLaterOnesDo() {
    Review review = draftWithContent();
    assertThat(review.currentVersion().orElseThrow().versionNumber()).isEqualTo(1);
    assertThat(review.currentVersion().orElseThrow().editReason()).isNull();

    Review edited = reloaded(review);
    assertThatThrownBy(() -> append(edited, "განახლებული", " ", LATER))
        .isInstanceOf(IllegalArgumentException.class);

    append(edited, "განახლებული", "fixed typos", LATER);
    assertThat(edited.currentVersion().orElseThrow().versionNumber()).isEqualTo(2);
    assertThat(edited.currentVersion().orElseThrow().editReason()).isEqualTo("fixed typos");
  }

  @Test
  void aLoadedReviewAppendsAtMostOneVersion() {
    // The aggregate no longer carries its history, so a second in-memory append would silently
    // drop the first from the stored trail. It must be saved and reloaded instead.
    Review review = draftWithContent();

    assertThatThrownBy(() -> append(review, "მეორე", "again", LATER))
        .isInstanceOf(IllegalStateException.class);

    Review edited = reloaded(review);
    append(edited, "მეორე", "again", LATER);
    assertThatThrownBy(() -> append(edited, "მესამე", "and again", LATER))
        .isInstanceOf(IllegalStateException.class);
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

    Review edited = reloaded(review);
    append(edited, "დამატებული დეტალები", "added details", EVEN_LATER);
    assertThat(edited.status()).isEqualTo(ReviewStatus.PENDING_MODERATION);
    assertThat(edited.currentVersion().orElseThrow().versionNumber()).isEqualTo(2);

    edited.publish(EVEN_LATER);
    // The first publication time is preserved across the re-moderation cycle.
    assertThat(edited.publishedAt()).contains(LATER.instant());
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

    Review terminal = reloaded(review);
    assertThatThrownBy(() -> append(terminal, "x", "why", LATER))
        .isInstanceOf(IllegalReviewStateTransitionException.class);
    assertThatThrownBy(() -> terminal.remove(LATER))
        .isInstanceOf(IllegalReviewStateTransitionException.class);
    assertThatThrownBy(
            () -> terminal.updateVerificationTier(VerificationTier.DOCUMENT_VERIFIED, LATER))
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
    ReviewVersion content = contentVersion(1);

    assertThatThrownBy(
            () ->
                Review.reconstitute(
                    ReviewId.of(UUID.randomUUID()),
                    PropertyRef.of(UUID.randomUUID()),
                    AuthorId.of(UUID.randomUUID()),
                    RelationshipType.OWNER,
                    null,
                    ReviewStatus.PUBLISHED,
                    content,
                    VerificationTier.UNVERIFIED,
                    null,
                    CREATED.instant(),
                    CREATED.instant(),
                    0L))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void reconstituteRejectsAModeratedReviewWithoutContent() {
    assertThatThrownBy(
            () ->
                Review.reconstitute(
                    ReviewId.of(UUID.randomUUID()),
                    PropertyRef.of(UUID.randomUUID()),
                    AuthorId.of(UUID.randomUUID()),
                    RelationshipType.OWNER,
                    null,
                    ReviewStatus.PENDING_MODERATION,
                    null,
                    VerificationTier.UNVERIFIED,
                    null,
                    CREATED.instant(),
                    CREATED.instant(),
                    0L))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static ReviewVersion contentVersion(int number) {
    return new ReviewVersion(
        UUID.randomUUID(),
        number,
        "en",
        "content",
        null,
        null,
        Recommendation.NEUTRAL,
        List.of(),
        number == 1 ? null : "reason",
        CREATED.instant());
  }
}

package com.example.geohousing.reviews.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.geohousing.reviews.domain.AuthorId;
import com.example.geohousing.reviews.domain.CategoryRating;
import com.example.geohousing.reviews.domain.IllegalReviewStateTransitionException;
import com.example.geohousing.reviews.domain.PropertyRef;
import com.example.geohousing.reviews.domain.Recommendation;
import com.example.geohousing.reviews.domain.RelationshipType;
import com.example.geohousing.reviews.domain.ResidencePeriod;
import com.example.geohousing.reviews.domain.Review;
import com.example.geohousing.reviews.domain.ReviewId;
import com.example.geohousing.reviews.domain.ReviewNotFoundException;
import com.example.geohousing.reviews.domain.ReviewStatus;
import com.example.geohousing.reviews.domain.VerificationTier;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReviewSubmissionServiceTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-07-20T10:00:00Z"), ZoneOffset.UTC);
  private static final PropertyRef PROPERTY = PropertyRef.of(UUID.randomUUID());
  private static final AuthorId AUTHOR = AuthorId.of(UUID.randomUUID());
  private static final AuthorId STRANGER = AuthorId.of(UUID.randomUUID());

  private final InMemoryReviewRepository repository = new InMemoryReviewRepository();

  private static ReviewContent content(String body) {
    return new ReviewContent(
        "ka",
        body,
        "quiet street",
        "no parking",
        Recommendation.RECOMMEND,
        List.of(CategoryRating.rated("NOISE", 4, null)));
  }

  private static SubmitReviewCommand submission() {
    return new SubmitReviewCommand(
        PROPERTY,
        AUTHOR,
        RelationshipType.CURRENT_RESIDENT,
        ResidencePeriod.of(LocalDate.of(2023, 3, 1), null),
        content("კარგი შენობა, მშვიდი მეზობლები"));
  }

  private ReviewSubmissionService serviceSeeing(PropertyLookup lookup) {
    return new ReviewSubmissionService(repository, lookup, CLOCK);
  }

  private ReviewSubmissionService service() {
    return serviceSeeing(ref -> Optional.of(new PropertyReviewability(ref, true)));
  }

  /** Applies a stored-state change the way an admin/moderation path would: load, mutate, save. */
  private Review storedAfter(ReviewId reviewId, java.util.function.Consumer<Review> change) {
    Review loaded = repository.findById(reviewId).orElseThrow();
    change.accept(loaded);
    repository.save(loaded);
    return loaded;
  }

  @Test
  void aSubmittedReviewGoesToModerationNotStraightToThePublicListing() {
    Review review = service().submit(submission());

    assertThat(review.status()).isEqualTo(ReviewStatus.PENDING_MODERATION);
    assertThat(review.publishedAt()).isEmpty();
    assertThat(review.verificationTier()).isEqualTo(VerificationTier.UNVERIFIED);
    assertThat(review.currentVersion().orElseThrow().versionNumber()).isEqualTo(1);
    assertThat(review.currentVersion().orElseThrow().editReason()).isNull();
    assertThat(repository.byId).containsKey(review.id());
  }

  @Test
  void aReviewOfAnUnknownPropertyIsRefused() {
    ReviewSubmissionService service = serviceSeeing(ref -> Optional.empty());

    assertThatThrownBy(() -> service.submit(submission()))
        .isInstanceOf(PropertyNotFoundForReviewException.class);
    assertThat(repository.byId).isEmpty();
  }

  @Test
  void aPropertyThatTakesNoNewReviewsIsRefused() {
    ReviewSubmissionService service =
        serviceSeeing(ref -> Optional.of(new PropertyReviewability(ref, false)));

    assertThatThrownBy(() -> service.submit(submission()))
        .isInstanceOf(PropertyNotReviewableException.class);
    assertThat(repository.byId).isEmpty();
  }

  @Test
  void aReviewOfAMergedPropertyAttachesToTheSurvivingOne() {
    PropertyRef survivor = PropertyRef.of(UUID.randomUUID());
    ReviewSubmissionService service =
        serviceSeeing(ref -> Optional.of(new PropertyReviewability(survivor, true)));

    Review review = service.submit(submission());

    assertThat(review.propertyRef()).isEqualTo(survivor);
  }

  @Test
  void anAuthorCannotHoldTwoLiveReviewsOfTheSameProperty() {
    ReviewSubmissionService service = service();
    Review first = service.submit(submission());

    assertThatThrownBy(() -> service.submit(submission()))
        .isInstanceOf(DuplicateReviewException.class)
        .extracting(thrown -> ((DuplicateReviewException) thrown).existingReviewId())
        .isEqualTo(first.id());
    assertThat(repository.byId).hasSize(1);
  }

  @Test
  void theOneLiveReviewSlotIsPerAuthorAndPerProperty() {
    ReviewSubmissionService service = service();
    service.submit(submission());

    PropertyRef otherProperty = PropertyRef.of(UUID.randomUUID());
    service.submit(
        new SubmitReviewCommand(
            otherProperty, AUTHOR, RelationshipType.OWNER, null, content("another building")));
    service.submit(
        new SubmitReviewCommand(
            PROPERTY, STRANGER, RelationshipType.FORMER_RESIDENT, null, content("my own view")));

    assertThat(repository.byId).hasSize(3);
  }

  @Test
  void aRejectedReviewFreesTheSlotForAFreshOne() {
    ReviewSubmissionService service = service();
    Review first = service.submit(submission());
    storedAfter(first.id(), review -> review.reject(CLOCK));

    Review second = service.submit(submission());

    assertThat(second.id()).isNotEqualTo(first.id());
    assertThat(repository.byId).hasSize(2);
  }

  @Test
  void anEditAppendsAVersionAndReturnsAPublishedReviewToModeration() {
    ReviewSubmissionService service = service();
    Review review = service.submit(submission());
    storedAfter(review.id(), stored -> stored.publish(CLOCK));
    int savesBefore = repository.saveCount;

    Review edited =
        service.edit(
            new EditReviewCommand(
                review.id(), AUTHOR, content("დაზუსტებული აღწერა"), "fixed a detail"));

    assertThat(edited.currentVersion().orElseThrow().versionNumber()).isEqualTo(2);
    assertThat(edited.currentVersion().orElseThrow().editReason()).isEqualTo("fixed a detail");
    assertThat(edited.status()).isEqualTo(ReviewStatus.PENDING_MODERATION);
    assertThat(repository.saveCount).isEqualTo(savesBefore + 1);

    Review stored = repository.findById(review.id()).orElseThrow();
    assertThat(stored.status()).isEqualTo(ReviewStatus.PENDING_MODERATION);
    assertThat(stored.currentVersion().orElseThrow().versionNumber()).isEqualTo(2);
  }

  @Test
  void anEditMustStateItsReason() {
    assertThatThrownBy(
            () ->
                new EditReviewCommand(
                    ReviewId.of(UUID.randomUUID()), AUTHOR, content("changed"), "  "))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void aStrangerCannotEditSomeoneElsesPublishedReview() {
    ReviewSubmissionService service = service();
    Review review = service.submit(submission());
    storedAfter(review.id(), stored -> stored.publish(CLOCK));

    assertThatThrownBy(
            () ->
                service.edit(
                    new EditReviewCommand(review.id(), STRANGER, content("rewritten"), "because")))
        .isInstanceOf(ReviewAccessDeniedException.class);
    assertThat(
            repository
                .findById(review.id())
                .orElseThrow()
                .currentVersion()
                .orElseThrow()
                .versionNumber())
        .isEqualTo(1);
  }

  @Test
  void aStrangerIsNotEvenToldThatAnUnpublishedReviewExists() {
    ReviewSubmissionService service = service();
    Review review = service.submit(submission());

    // Still PENDING_MODERATION: reporting "forbidden" here would confirm that this author
    // reviewed this property.
    assertThatThrownBy(
            () ->
                service.edit(
                    new EditReviewCommand(review.id(), STRANGER, content("rewritten"), "because")))
        .isInstanceOf(ReviewNotFoundException.class);
  }

  @Test
  void editingAReviewThatDoesNotExistIsNotFound() {
    assertThatThrownBy(
            () ->
                service()
                    .edit(
                        new EditReviewCommand(
                            ReviewId.of(UUID.randomUUID()), AUTHOR, content("x"), "because")))
        .isInstanceOf(ReviewNotFoundException.class);
  }

  @Test
  void anAuthorCannotQuietlyRewriteAReviewAModeratorHidThenHaveItRepublished() {
    ReviewSubmissionService service = service();
    Review review = service.submit(submission());
    storedAfter(
        review.id(),
        stored -> {
          stored.publish(CLOCK);
          stored.hide(CLOCK);
        });

    assertThatThrownBy(
            () ->
                service.edit(
                    new EditReviewCommand(review.id(), AUTHOR, content("softened"), "appeal")))
        .isInstanceOf(IllegalReviewStateTransitionException.class);

    Review stored = repository.findById(review.id()).orElseThrow();
    assertThat(stored.status()).isEqualTo(ReviewStatus.HIDDEN);
    assertThat(stored.currentVersion().orElseThrow().versionNumber()).isEqualTo(1);
  }

  @Test
  void aRemovedReviewCannotBeEditedBackToLife() {
    ReviewSubmissionService service = service();
    Review review = service.submit(submission());
    storedAfter(review.id(), stored -> stored.remove(CLOCK));

    assertThatThrownBy(
            () ->
                service.edit(new EditReviewCommand(review.id(), AUTHOR, content("back"), "retry")))
        .isInstanceOf(IllegalReviewStateTransitionException.class);
  }
}

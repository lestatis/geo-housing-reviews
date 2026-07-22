package com.example.geohousing.reviews.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.geohousing.reviews.domain.AuthorId;
import com.example.geohousing.reviews.domain.PropertyRef;
import com.example.geohousing.reviews.domain.Recommendation;
import com.example.geohousing.reviews.domain.RelationshipType;
import com.example.geohousing.reviews.domain.Review;
import com.example.geohousing.reviews.domain.ReviewId;
import com.example.geohousing.reviews.domain.ReviewNotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReviewQueryServiceTest {

  private static final Instant BASE = Instant.parse("2026-07-20T10:00:00Z");
  private static final PropertyRef PROPERTY = PropertyRef.of(UUID.randomUUID());
  private static final AuthorId AUTHOR = AuthorId.of(UUID.randomUUID());
  private static final AuthorId STRANGER = AuthorId.of(UUID.randomUUID());

  private final InMemoryReviewRepository repository = new InMemoryReviewRepository();
  private final ReviewQueryService service = new ReviewQueryService(repository);

  private Review storePending(AuthorId author, PropertyRef property, Instant at) {
    Clock clock = Clock.fixed(at, ZoneOffset.UTC);
    Review review =
        Review.create(
            ReviewId.of(UUID.randomUUID()),
            property,
            author,
            RelationshipType.CURRENT_RESIDENT,
            null,
            clock);
    review.appendVersion("ka", "body", null, null, Recommendation.NEUTRAL, List.of(), null, clock);
    review.submit(clock);
    repository.create(review);
    return review;
  }

  private Review storePublished(AuthorId author, PropertyRef property, Instant publishedAt) {
    Review review = storePending(author, property, publishedAt);
    review.publish(Clock.fixed(publishedAt, ZoneOffset.UTC));
    repository.save(review);
    return review;
  }

  @Test
  void aPublishedReviewIsVisibleToAnyone() {
    Review review = storePublished(AUTHOR, PROPERTY, BASE);

    assertThat(service.getById(review.id(), ReviewViewer.anonymous()).id()).isEqualTo(review.id());
    assertThat(service.getById(review.id(), ReviewViewer.user(STRANGER)).id())
        .isEqualTo(review.id());
  }

  @Test
  void aReviewAwaitingModerationIsVisibleOnlyToItsAuthorAndModerators() {
    Review review = storePending(AUTHOR, PROPERTY, BASE);

    assertThat(service.getById(review.id(), ReviewViewer.user(AUTHOR)).id()).isEqualTo(review.id());
    assertThat(service.getById(review.id(), ReviewViewer.moderator(STRANGER)).id())
        .isEqualTo(review.id());
    assertThatThrownBy(() -> service.getById(review.id(), ReviewViewer.user(STRANGER)))
        .isInstanceOf(ReviewNotFoundException.class);
    assertThatThrownBy(() -> service.getById(review.id(), ReviewViewer.anonymous()))
        .isInstanceOf(ReviewNotFoundException.class);
  }

  @Test
  void aHiddenReviewDisappearsForEveryoneButItsAuthorAndModerators() {
    Review review = storePublished(AUTHOR, PROPERTY, BASE);
    review.hide(Clock.fixed(BASE, ZoneOffset.UTC));
    repository.save(review);

    assertThat(service.getById(review.id(), ReviewViewer.user(AUTHOR)).id()).isEqualTo(review.id());
    assertThat(service.getById(review.id(), ReviewViewer.moderator(STRANGER)).id())
        .isEqualTo(review.id());
    assertThatThrownBy(() -> service.getById(review.id(), ReviewViewer.anonymous()))
        .isInstanceOf(ReviewNotFoundException.class);
  }

  @Test
  void anUnknownReviewIsNotFound() {
    assertThatThrownBy(
            () -> service.getById(ReviewId.of(UUID.randomUUID()), ReviewViewer.moderator(AUTHOR)))
        .isInstanceOf(ReviewNotFoundException.class);
  }

  @Test
  void thePublicListingCarriesOnlyPublishedReviewsEvenForAModerator() {
    Review published = storePublished(AUTHOR, PROPERTY, BASE);
    storePending(STRANGER, PROPERTY, BASE); // still awaiting moderation

    ReviewPage page = service.listPublished(PROPERTY, null, null);

    assertThat(page.reviews()).extracting(Review::id).containsExactly(published.id());
    assertThat(page.next()).isEmpty();
  }

  @Test
  void theListingIsScopedToOneProperty() {
    Review here = storePublished(AUTHOR, PROPERTY, BASE);
    storePublished(AUTHOR, PropertyRef.of(UUID.randomUUID()), BASE);

    assertThat(service.listPublished(PROPERTY, null, null).reviews())
        .extracting(Review::id)
        .containsExactly(here.id());
  }

  @Test
  void pagingWalksNewestFirstAndStopsWhenTheCursorRunsOut() {
    Review oldest = storePublished(AUTHOR, PROPERTY, BASE);
    Review middle = storePublished(STRANGER, PROPERTY, BASE.plusSeconds(60));
    Review newest = storePublished(AuthorId.of(UUID.randomUUID()), PROPERTY, BASE.plusSeconds(120));

    ReviewPage first = service.listPublished(PROPERTY, null, 2);
    assertThat(first.reviews()).extracting(Review::id).containsExactly(newest.id(), middle.id());
    assertThat(first.next()).isPresent();

    ReviewPage second = service.listPublished(PROPERTY, first.nextCursor(), 2);
    assertThat(second.reviews()).extracting(Review::id).containsExactly(oldest.id());
    assertThat(second.next()).isEmpty();
  }

  @Test
  void anOversizedPageRequestIsClampedRatherThanRefused() {
    for (int i = 0; i < ReviewQueryService.MAX_PAGE_SIZE + 5; i++) {
      storePublished(AuthorId.of(UUID.randomUUID()), PROPERTY, BASE.plusSeconds(i));
    }

    assertThat(service.listPublished(PROPERTY, null, 5000).reviews())
        .hasSize(ReviewQueryService.MAX_PAGE_SIZE);
    assertThat(service.listPublished(PROPERTY, null, 0).reviews()).hasSize(1);
    assertThat(service.listPublished(PROPERTY, null, null).reviews())
        .hasSize(ReviewQueryService.DEFAULT_PAGE_SIZE);
  }
}

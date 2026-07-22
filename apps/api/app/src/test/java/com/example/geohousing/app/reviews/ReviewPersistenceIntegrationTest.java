package com.example.geohousing.app.reviews;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.geohousing.reviews.application.ReviewCursor;
import com.example.geohousing.reviews.application.ReviewPage;
import com.example.geohousing.reviews.application.ReviewRepository;
import com.example.geohousing.reviews.domain.AuthorId;
import com.example.geohousing.reviews.domain.CategoryRating;
import com.example.geohousing.reviews.domain.PropertyRef;
import com.example.geohousing.reviews.domain.Recommendation;
import com.example.geohousing.reviews.domain.RelationshipType;
import com.example.geohousing.reviews.domain.ResidencePeriod;
import com.example.geohousing.reviews.domain.Review;
import com.example.geohousing.reviews.domain.ReviewId;
import com.example.geohousing.reviews.domain.ReviewStatus;
import com.example.geohousing.reviews.domain.ReviewVersion;
import com.example.geohousing.reviews.domain.ReviewVersionConflictException;
import com.example.geohousing.reviews.domain.VerificationTier;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * The review aggregate against a real database. Because the application boots with {@code
 * ddl-auto=validate}, every mapping here is checked against the migrated schema before a single
 * assertion runs.
 */
@Testcontainers
@SpringBootTest
class ReviewPersistenceIntegrationTest {

  private static final Instant CREATED_AT = Instant.parse("2026-07-20T10:00:00Z");
  private static final Clock CLOCK = Clock.fixed(CREATED_AT, ZoneOffset.UTC);

  @Container @ServiceConnection
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(
          DockerImageName.parse("postgis/postgis:18-3.6").asCompatibleSubstituteFor("postgres"));

  @Autowired private ReviewRepository reviews;

  private static Review draft(PropertyRef property, AuthorId author, Clock clock) {
    return Review.create(
        ReviewId.of(UUID.randomUUID()),
        property,
        author,
        RelationshipType.FORMER_RESIDENT,
        ResidencePeriod.of(LocalDate.of(2022, 4, 1), LocalDate.of(2025, 9, 30)),
        clock);
  }

  private static void addContent(Review review, String body, String editReason, Clock clock) {
    review.appendVersion(
        "ka",
        body,
        "მშვიდი ეზო",
        "ლიფტი ხშირად ჩერდება",
        Recommendation.RECOMMEND,
        List.of(
            CategoryRating.rated("NOISE", 4, "thin walls"),
            CategoryRating.notApplicable("PARKING", null)),
        editReason,
        clock);
  }

  private Review storePublished(PropertyRef property, AuthorId author, Instant publishedAt) {
    Clock clock = Clock.fixed(publishedAt, ZoneOffset.UTC);
    Review review = draft(property, author, clock);
    addContent(review, "კარგი შენობა", null, clock);
    review.submit(clock);
    review.publish(clock);
    reviews.create(review);
    return review;
  }

  @Test
  void persistsAndReconstitutesTheWholeAggregate() {
    PropertyRef property = PropertyRef.of(UUID.randomUUID());
    AuthorId author = AuthorId.of(UUID.randomUUID());
    Review review = draft(property, author, CLOCK);
    addContent(review, "კარგი შენობა, მშვიდი მეზობლები", null, CLOCK);
    review.submit(CLOCK);

    reviews.create(review);
    Review loaded = reviews.findById(review.id()).orElseThrow();

    assertThat(loaded.id()).isEqualTo(review.id());
    assertThat(loaded.propertyRef()).isEqualTo(property);
    assertThat(loaded.authorId()).isEqualTo(author);
    assertThat(loaded.relationshipType()).isEqualTo(RelationshipType.FORMER_RESIDENT);
    assertThat(loaded.residencePeriod())
        .contains(ResidencePeriod.of(LocalDate.of(2022, 4, 1), LocalDate.of(2025, 9, 30)));
    assertThat(loaded.status()).isEqualTo(ReviewStatus.PENDING_MODERATION);
    assertThat(loaded.verificationTier()).isEqualTo(VerificationTier.UNVERIFIED);
    assertThat(loaded.publishedAt()).isEmpty();

    ReviewVersion version = loaded.currentVersion().orElseThrow();
    assertThat(version.versionNumber()).isEqualTo(1);
    assertThat(version.body()).isEqualTo("კარგი შენობა, მშვიდი მეზობლები");
    assertThat(version.pros()).isEqualTo("მშვიდი ეზო");
    assertThat(version.recommendation()).isEqualTo(Recommendation.RECOMMEND);
    assertThat(version.editReason()).isNull();
    assertThat(version.ratings())
        .containsExactlyInAnyOrder(
            CategoryRating.rated("NOISE", 4, "thin walls"),
            CategoryRating.notApplicable("PARKING", null));
  }

  @Test
  void anEditAppendsAVersionAndLeavesTheEarlierOneUntouched() {
    Review review =
        storePublished(
            PropertyRef.of(UUID.randomUUID()), AuthorId.of(UUID.randomUUID()), CREATED_AT);
    Review loaded = reviews.findById(review.id()).orElseThrow();
    ReviewVersion original = loaded.currentVersion().orElseThrow();

    Clock later = Clock.fixed(CREATED_AT.plusSeconds(3600), ZoneOffset.UTC);
    addContent(loaded, "განახლებული აღწერა", "დავაზუსტე დეტალები", later);
    reviews.save(loaded);

    Review reloaded = reviews.findById(review.id()).orElseThrow();
    assertThat(reloaded.versions()).hasSize(2);
    assertThat(reloaded.versions().get(0).id()).isEqualTo(original.id());
    assertThat(reloaded.versions().get(0).body()).isEqualTo(original.body());
    assertThat(reloaded.currentVersion().orElseThrow().versionNumber()).isEqualTo(2);
    assertThat(reloaded.currentVersion().orElseThrow().editReason())
        .isEqualTo("დავაზუსტე დეტალები");
    // Editing a published review sends it back for re-moderation, but keeps the first publication.
    assertThat(reloaded.status()).isEqualTo(ReviewStatus.PENDING_MODERATION);
    assertThat(reloaded.publishedAt()).contains(CREATED_AT);
  }

  @Test
  void aStaleWriteIsRefusedRatherThanOverwritingAConcurrentOne() {
    Review review =
        storePublished(
            PropertyRef.of(UUID.randomUUID()), AuthorId.of(UUID.randomUUID()), CREATED_AT);

    Review first = reviews.findById(review.id()).orElseThrow();
    Review second = reviews.findById(review.id()).orElseThrow();

    Clock later = Clock.fixed(CREATED_AT.plusSeconds(60), ZoneOffset.UTC);
    addContent(first, "პირველი რედაქტირება", "first", later);
    reviews.save(first);

    addContent(second, "მეორე რედაქტირება", "second", later);
    assertThatThrownBy(() -> reviews.save(second))
        .isInstanceOf(ReviewVersionConflictException.class);

    Review reloaded = reviews.findById(review.id()).orElseThrow();
    assertThat(reloaded.versions()).hasSize(2);
    assertThat(reloaded.currentVersion().orElseThrow().editReason()).isEqualTo("first");
  }

  @Test
  void theDatabaseAlsoRefusesASecondLiveReviewOfTheSameProperty() {
    PropertyRef property = PropertyRef.of(UUID.randomUUID());
    AuthorId author = AuthorId.of(UUID.randomUUID());
    storePublished(property, author, CREATED_AT);

    Review second = draft(property, author, CLOCK);
    addContent(second, "მეორე მიმოხილვა", null, CLOCK);
    second.submit(CLOCK);

    // The application refuses this first; the partial unique index is the backstop.
    assertThatThrownBy(() -> reviews.create(second))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void aRejectedReviewNoLongerOccupiesTheAuthorsSlot() {
    PropertyRef property = PropertyRef.of(UUID.randomUUID());
    AuthorId author = AuthorId.of(UUID.randomUUID());
    Review first = draft(property, author, CLOCK);
    addContent(first, "პირველი", null, CLOCK);
    first.submit(CLOCK);
    reviews.create(first);

    assertThat(reviews.findLiveByAuthorAndProperty(author, property)).isPresent();

    Review loaded = reviews.findById(first.id()).orElseThrow();
    loaded.reject(CLOCK);
    reviews.save(loaded);

    assertThat(reviews.findLiveByAuthorAndProperty(author, property)).isEmpty();

    Review fresh = draft(property, author, CLOCK);
    addContent(fresh, "ახალი მიმოხილვა", null, CLOCK);
    fresh.submit(CLOCK);
    reviews.create(fresh);
    assertThat(reviews.findById(fresh.id())).isPresent();
  }

  @Test
  void theLiveLookupIsScopedToOneAuthorAndOneProperty() {
    PropertyRef property = PropertyRef.of(UUID.randomUUID());
    AuthorId author = AuthorId.of(UUID.randomUUID());
    storePublished(property, author, CREATED_AT);

    assertThat(reviews.findLiveByAuthorAndProperty(AuthorId.of(UUID.randomUUID()), property))
        .isEmpty();
    assertThat(reviews.findLiveByAuthorAndProperty(author, PropertyRef.of(UUID.randomUUID())))
        .isEmpty();
  }

  @Test
  void thePublishedListingPagesNewestFirstAndCarriesOnlyPublishedReviews() {
    PropertyRef property = PropertyRef.of(UUID.randomUUID());
    Review oldest = storePublished(property, AuthorId.of(UUID.randomUUID()), CREATED_AT);
    Review middle =
        storePublished(property, AuthorId.of(UUID.randomUUID()), CREATED_AT.plusSeconds(60));
    Review newest =
        storePublished(property, AuthorId.of(UUID.randomUUID()), CREATED_AT.plusSeconds(120));

    Review pending = draft(property, AuthorId.of(UUID.randomUUID()), CLOCK);
    addContent(pending, "ჯერ არ გამოქვეყნებულა", null, CLOCK);
    pending.submit(CLOCK);
    reviews.create(pending);

    ReviewPage first = reviews.findPublishedByProperty(property, null, 2);
    assertThat(first.reviews()).extracting(Review::id).containsExactly(newest.id(), middle.id());
    assertThat(first.next()).isPresent();

    ReviewCursor cursor = first.nextCursor();
    ReviewPage second = reviews.findPublishedByProperty(property, cursor, 2);
    assertThat(second.reviews()).extracting(Review::id).containsExactly(oldest.id());
    assertThat(second.next()).isEmpty();
  }

  @Test
  void reviewsPublishedInTheSameInstantStillPageWithoutRepeatingOrSkipping() {
    PropertyRef property = PropertyRef.of(UUID.randomUUID());
    Instant sameMoment = CREATED_AT.plusSeconds(500);
    for (int i = 0; i < 5; i++) {
      storePublished(property, AuthorId.of(UUID.randomUUID()), sameMoment);
    }

    ReviewPage first = reviews.findPublishedByProperty(property, null, 2);
    ReviewPage second = reviews.findPublishedByProperty(property, first.nextCursor(), 2);
    ReviewPage third = reviews.findPublishedByProperty(property, second.nextCursor(), 2);

    List<ReviewId> seen =
        List.of(
                first.reviews().stream().map(Review::id).toList(),
                second.reviews().stream().map(Review::id).toList(),
                third.reviews().stream().map(Review::id).toList())
            .stream()
            .flatMap(List::stream)
            .toList();

    assertThat(seen).hasSize(5).doesNotHaveDuplicates();
    assertThat(third.next()).isEmpty();
  }

  @Test
  void aListingOfAPropertyWithoutReviewsIsAnEmptyLastPage() {
    ReviewPage page = reviews.findPublishedByProperty(PropertyRef.of(UUID.randomUUID()), null, 20);

    assertThat(page.reviews()).isEmpty();
    assertThat(page.next()).isEmpty();
  }
}

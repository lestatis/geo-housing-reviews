package com.example.geohousing.reviews.infrastructure.persistence;

import com.example.geohousing.reviews.application.ReviewCursor;
import com.example.geohousing.reviews.application.ReviewPage;
import com.example.geohousing.reviews.application.ReviewRepository;
import com.example.geohousing.reviews.domain.AuthorId;
import com.example.geohousing.reviews.domain.PropertyRef;
import com.example.geohousing.reviews.domain.Review;
import com.example.geohousing.reviews.domain.ReviewId;
import com.example.geohousing.reviews.domain.ReviewNotFoundException;
import com.example.geohousing.reviews.domain.ReviewStatus;
import com.example.geohousing.reviews.domain.ReviewVersion;
import com.example.geohousing.reviews.domain.ReviewVersionConflictException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * JPA implementation of the {@link ReviewRepository} port. Reads load the review row plus its
 * current version only; the older versions stay where they are, as the stored moderation trail.
 *
 * <p>Version rows are written explicitly and never updated. The adapter is the last line of defence
 * for two invariants the aggregate can no longer see (it carries only its current version): version
 * numbers must follow the stored one directly, and a stored version must never be replaced by a
 * different one under the same number.
 */
@Repository
public class JpaReviewRepository implements ReviewRepository {

  private final SpringDataReviewRepository reviews;
  private final SpringDataReviewVersionRepository versions;

  public JpaReviewRepository(
      SpringDataReviewRepository reviews, SpringDataReviewVersionRepository versions) {
    this.reviews = reviews;
    this.versions = versions;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<Review> findById(ReviewId reviewId) {
    return reviews.findById(reviewId.value()).map(this::toDomainWithCurrentVersion);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<Review> findLiveByAuthorAndProperty(AuthorId authorId, PropertyRef propertyRef) {
    return reviews
        .findLive(authorId.value(), propertyRef.value())
        .map(this::toDomainWithCurrentVersion);
  }

  /**
   * The review row is inserted first, pointing at a version row that does not exist yet — {@code
   * V4.2} defers that foreign key to commit, by which time the version row is in place.
   */
  @Override
  @Transactional
  public void create(Review review) {
    reviews.save(ReviewJpaMapper.toEntity(review));
    review
        .currentVersion()
        .ifPresent(
            current ->
                versions.save(ReviewJpaMapper.toVersionEntity(review.id().value(), current)));
  }

  @Override
  @Transactional
  public void save(Review review) {
    ReviewJpaEntity entity =
        reviews
            .findById(review.id().value())
            .orElseThrow(() -> new ReviewNotFoundException(review.id()));
    if (entity.version() != review.version()) {
      throw new ReviewVersionConflictException(
          "review " + review.id().value() + " was modified concurrently");
    }

    ReviewVersionJpaEntity storedCurrent =
        entity.currentVersionId() == null
            ? null
            : versions.findById(entity.currentVersionId()).orElseThrow();
    ReviewVersion appended = appendedVersion(review, storedCurrent);
    if (appended != null) {
      versions.save(ReviewJpaMapper.toVersionEntity(review.id().value(), appended));
    }

    entity.apply(
        review.status(),
        review.verificationTier(),
        review.currentVersion().map(ReviewVersion::id).orElse(null),
        review.publishedAt().orElse(null),
        review.updatedAt());
  }

  /**
   * The version to insert, or null when the content did not change. Anything other than "same
   * version as stored" or "exactly the next number" means the caller manufactured history the store
   * never saw — refused loudly, because the stored trail is a moderation guarantee.
   */
  private static ReviewVersion appendedVersion(
      Review review, ReviewVersionJpaEntity storedCurrent) {
    int storedNumber = storedCurrent == null ? 0 : storedCurrent.versionNumber();
    ReviewVersion current = review.currentVersion().orElse(null);
    int number = current == null ? 0 : current.versionNumber();

    if (number == storedNumber) {
      boolean sameVersion =
          (current == null && storedCurrent == null)
              || (current != null
                  && storedCurrent != null
                  && Objects.equals(current.id(), storedCurrent.id()));
      if (!sameVersion) {
        throw new IllegalStateException(
            "stored content versions cannot be rewritten: " + review.id().value());
      }
      return null;
    }
    if (number == storedNumber + 1 && current != null) {
      return current;
    }
    throw new IllegalStateException(
        "review "
            + review.id().value()
            + " went from version "
            + storedNumber
            + " to "
            + number
            + "; the stored trail must not skip or lose versions");
  }

  @Override
  @Transactional(readOnly = true)
  public ReviewPage findPublishedByProperty(
      PropertyRef propertyRef, ReviewCursor after, int limit) {
    // One more than asked for, so a full page can be told apart from the last one without a
    // second count query.
    Limit probe = Limit.of(limit + 1);
    List<UUID> ids =
        after == null
            ? reviews.findPublishedIds(propertyRef.value(), ReviewStatus.PUBLISHED, probe)
            : reviews.findPublishedIdsAfter(
                propertyRef.value(),
                ReviewStatus.PUBLISHED,
                after.publishedAt(),
                after.reviewId().value(),
                probe);

    boolean hasMore = ids.size() > limit;
    List<UUID> pageIds = hasMore ? ids.subList(0, limit) : ids;
    List<Review> page = loadInOrder(pageIds);

    if (!hasMore || page.isEmpty()) {
      return ReviewPage.lastPage(page);
    }
    Review last = page.get(page.size() - 1);
    return new ReviewPage(page, new ReviewCursor(last.publishedAt().orElseThrow(), last.id()));
  }

  /**
   * Loads a page of reviews and their current versions in two bulk queries, restoring the paged
   * order ({@code in (:ids)} does not preserve it).
   */
  private List<Review> loadInOrder(List<UUID> ids) {
    if (ids.isEmpty()) {
      return List.of();
    }
    Map<UUID, ReviewJpaEntity> byId =
        reviews.findAllById(ids).stream()
            .collect(Collectors.toMap(ReviewJpaEntity::id, Function.identity()));
    Map<UUID, ReviewVersionJpaEntity> currentById =
        versions
            .findAllById(
                byId.values().stream()
                    .map(ReviewJpaEntity::currentVersionId)
                    .filter(Objects::nonNull)
                    .toList())
            .stream()
            .collect(Collectors.toMap(ReviewVersionJpaEntity::id, Function.identity()));

    List<Review> ordered = new ArrayList<>(ids.size());
    for (UUID id : ids) {
      ReviewJpaEntity entity = byId.get(id);
      if (entity != null) {
        ordered.add(ReviewJpaMapper.toDomain(entity, currentById.get(entity.currentVersionId())));
      }
    }
    return List.copyOf(ordered);
  }

  private Review toDomainWithCurrentVersion(ReviewJpaEntity entity) {
    ReviewVersionJpaEntity current =
        entity.currentVersionId() == null
            ? null
            : versions.findById(entity.currentVersionId()).orElseThrow();
    return ReviewJpaMapper.toDomain(entity, current);
  }
}

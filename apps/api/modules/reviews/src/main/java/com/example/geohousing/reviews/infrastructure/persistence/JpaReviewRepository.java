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
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** JPA implementation of the {@link ReviewRepository} port. */
@Repository
public class JpaReviewRepository implements ReviewRepository {

  private final SpringDataReviewRepository reviews;

  public JpaReviewRepository(SpringDataReviewRepository reviews) {
    this.reviews = reviews;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<Review> findById(ReviewId reviewId) {
    return reviews.findById(reviewId.value()).map(ReviewJpaMapper::toDomain);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<Review> findLiveByAuthorAndProperty(AuthorId authorId, PropertyRef propertyRef) {
    return reviews.findLive(authorId.value(), propertyRef.value()).map(ReviewJpaMapper::toDomain);
  }

  @Override
  @Transactional
  public void create(Review review) {
    reviews.save(ReviewJpaMapper.toEntity(review));
  }

  /**
   * Loads the stored review and applies the aggregate's changes to it, rather than merging a
   * detached copy: content versions are append-only, so writing back a whole graph could only ever
   * rewrite history that is meant to be immutable.
   */
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

    List<ReviewVersionJpaEntity> appended =
        review.versions().stream()
            .skip(entity.versions().size())
            .map(ReviewJpaMapper::toEntity)
            .toList();
    requireAppendOnly(review, entity, appended);

    entity.apply(
        review.status(),
        review.verificationTier(),
        review.publishedAt().orElse(null),
        review.updatedAt(),
        appended);
  }

  private static void requireAppendOnly(
      Review review, ReviewJpaEntity entity, List<ReviewVersionJpaEntity> appended) {
    if (review.versions().size() < entity.versions().size()) {
      throw new IllegalStateException(
          "a review cannot lose content versions: " + review.id().value());
    }
    List<ReviewVersion> retained = review.versions().subList(0, entity.versions().size());
    for (int i = 0; i < retained.size(); i++) {
      if (!retained.get(i).id().equals(entity.versions().get(i).id())) {
        throw new IllegalStateException(
            "stored content versions cannot be rewritten: " + review.id().value());
      }
    }
    if (appended.size() != review.versions().size() - entity.versions().size()) {
      throw new IllegalStateException("unexpected version count for " + review.id().value());
    }
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

  /** {@code in (:ids)} does not preserve order, so the paged order is restored here. */
  private List<Review> loadInOrder(List<UUID> ids) {
    if (ids.isEmpty()) {
      return List.of();
    }
    Map<UUID, ReviewJpaEntity> byId =
        reviews.findAllWithVersions(ids).stream()
            .collect(Collectors.toMap(ReviewJpaEntity::id, Function.identity()));
    List<Review> ordered = new ArrayList<>(ids.size());
    for (UUID id : ids) {
      ReviewJpaEntity entity = byId.get(id);
      if (entity != null) {
        ordered.add(ReviewJpaMapper.toDomain(entity));
      }
    }
    return List.copyOf(ordered);
  }
}

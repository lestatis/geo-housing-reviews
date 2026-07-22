package com.example.geohousing.reviews.application;

import com.example.geohousing.reviews.domain.AuthorId;
import com.example.geohousing.reviews.domain.PropertyRef;
import com.example.geohousing.reviews.domain.Review;
import com.example.geohousing.reviews.domain.ReviewId;
import com.example.geohousing.reviews.domain.ReviewStatus;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory {@link ReviewRepository} for use-case tests. It keeps the aggregate instances rather
 * than copies, which is enough to observe what the services do; the real ordering, paging and
 * concurrency semantics are proven against Postgres in the persistence chunk.
 */
final class InMemoryReviewRepository implements ReviewRepository {

  final Map<ReviewId, Review> byId = new LinkedHashMap<>();
  int saveCount;

  @Override
  public Optional<Review> findById(ReviewId reviewId) {
    return Optional.ofNullable(byId.get(reviewId));
  }

  @Override
  public Optional<Review> findLiveByAuthorAndProperty(AuthorId authorId, PropertyRef propertyRef) {
    return byId.values().stream()
        .filter(review -> review.authorId().equals(authorId))
        .filter(review -> review.propertyRef().equals(propertyRef))
        .filter(review -> !review.isTerminal())
        .findFirst();
  }

  @Override
  public void create(Review review) {
    byId.put(review.id(), review);
  }

  @Override
  public void save(Review review) {
    byId.put(review.id(), review);
    saveCount++;
  }

  @Override
  public ReviewPage findPublishedByProperty(
      PropertyRef propertyRef, ReviewCursor after, int limit) {
    List<Review> ordered =
        byId.values().stream()
            .filter(review -> review.propertyRef().equals(propertyRef))
            .filter(review -> review.status() == ReviewStatus.PUBLISHED)
            .sorted(
                Comparator.comparing((Review review) -> review.publishedAt().orElseThrow())
                    .reversed()
                    .thenComparing(review -> review.id().value(), Comparator.reverseOrder()))
            .filter(review -> after == null || isAfter(review, after))
            .toList();

    List<Review> page = new ArrayList<>(ordered.subList(0, Math.min(limit, ordered.size())));
    if (ordered.size() <= limit) {
      return ReviewPage.lastPage(page);
    }
    Review last = page.get(page.size() - 1);
    return new ReviewPage(page, new ReviewCursor(last.publishedAt().orElseThrow(), last.id()));
  }

  private static boolean isAfter(Review review, ReviewCursor cursor) {
    int byTime = review.publishedAt().orElseThrow().compareTo(cursor.publishedAt());
    if (byTime != 0) {
      return byTime < 0;
    }
    return review.id().value().compareTo(cursor.reviewId().value()) < 0;
  }
}

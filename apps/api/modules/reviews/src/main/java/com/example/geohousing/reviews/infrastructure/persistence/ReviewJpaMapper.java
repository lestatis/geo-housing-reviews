package com.example.geohousing.reviews.infrastructure.persistence;

import com.example.geohousing.reviews.domain.AuthorId;
import com.example.geohousing.reviews.domain.CategoryRating;
import com.example.geohousing.reviews.domain.PropertyRef;
import com.example.geohousing.reviews.domain.ResidencePeriod;
import com.example.geohousing.reviews.domain.Review;
import com.example.geohousing.reviews.domain.ReviewId;
import com.example.geohousing.reviews.domain.ReviewVersion;
import java.util.List;
import java.util.UUID;

final class ReviewJpaMapper {

  private ReviewJpaMapper() {}

  static ReviewJpaEntity toEntity(Review review) {
    List<ReviewVersionJpaEntity> versions =
        review.versions().stream().map(ReviewJpaMapper::toEntity).toList();
    UUID currentVersionId = versions.isEmpty() ? null : versions.get(versions.size() - 1).id();
    return new ReviewJpaEntity(
        review.id().value(),
        review.propertyRef().value(),
        review.authorId().value(),
        review.relationshipType(),
        review.residencePeriod().map(ResidencePeriod::from).orElse(null),
        review.residencePeriod().map(ResidencePeriod::to).orElse(null),
        review.status(),
        currentVersionId,
        review.verificationTier(),
        review.publishedAt().orElse(null),
        review.createdAt(),
        review.updatedAt(),
        review.version(),
        versions);
  }

  static ReviewVersionJpaEntity toEntity(ReviewVersion version) {
    // Category ratings are value objects with no identity of their own; the surrogate row id is a
    // persistence detail generated here, as in the properties module.
    List<CategoryRatingJpaEntity> ratings =
        version.ratings().stream()
            .map(
                rating ->
                    new CategoryRatingJpaEntity(
                        UUID.randomUUID(),
                        rating.category(),
                        rating.value() == null ? null : rating.value().shortValue(),
                        rating.notApplicable(),
                        rating.note(),
                        rating.categorySetVersion(),
                        version.createdAt()))
            .toList();
    return new ReviewVersionJpaEntity(
        version.id(),
        version.versionNumber(),
        version.locale(),
        version.body(),
        version.pros(),
        version.cons(),
        version.recommendation(),
        version.editReason(),
        version.createdAt(),
        ratings);
  }

  static Review toDomain(ReviewJpaEntity entity) {
    ResidencePeriod residencePeriod =
        entity.residenceFrom() == null && entity.residenceTo() == null
            ? null
            : ResidencePeriod.of(entity.residenceFrom(), entity.residenceTo());

    List<ReviewVersion> versions =
        entity.versions().stream().map(ReviewJpaMapper::toDomain).toList();

    return Review.reconstitute(
        ReviewId.of(entity.id()),
        PropertyRef.of(entity.propertyId()),
        AuthorId.of(entity.authorAccountId()),
        entity.relationshipType(),
        residencePeriod,
        entity.status(),
        versions,
        entity.verificationTier(),
        entity.publishedAt(),
        entity.createdAt(),
        entity.updatedAt(),
        entity.version());
  }

  private static ReviewVersion toDomain(ReviewVersionJpaEntity entity) {
    List<CategoryRating> ratings =
        entity.ratings().stream()
            .map(
                rating ->
                    new CategoryRating(
                        rating.category(),
                        rating.value() == null ? null : rating.value().intValue(),
                        rating.notApplicable(),
                        rating.note(),
                        rating.categorySetVersion()))
            .toList();
    return new ReviewVersion(
        entity.id(),
        entity.versionNumber(),
        entity.locale(),
        entity.body(),
        entity.pros(),
        entity.cons(),
        entity.recommendation(),
        ratings,
        entity.editReason(),
        entity.createdAt());
  }
}

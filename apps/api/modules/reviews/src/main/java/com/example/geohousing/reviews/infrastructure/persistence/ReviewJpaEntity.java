package com.example.geohousing.reviews.infrastructure.persistence;

import com.example.geohousing.reviews.domain.RelationshipType;
import com.example.geohousing.reviews.domain.ReviewStatus;
import com.example.geohousing.reviews.domain.VerificationTier;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * The review aggregate root's own row. Content versions are separate {@link ReviewVersionJpaEntity}
 * rows written explicitly by the adapter, not a mapped collection: the aggregate carries only its
 * current version, and mapping the whole history here would drag every edit into every read.
 *
 * <p>{@code property_id} and {@code author_account_id} are plain UUID columns, not associations:
 * they point into other modules, which own their own tables. {@code current_version_id} is a plain
 * column too — its foreign key is deferred ({@code V4.2}) because the review and its versions
 * reference each other, so the review row can be inserted first and the version row it points at
 * can follow inside the same transaction.
 */
@Entity
@Table(schema = "reviews", name = "review")
class ReviewJpaEntity {

  @Id private UUID id;

  @Column(name = "property_id", nullable = false)
  private UUID propertyId;

  @Column(name = "author_account_id", nullable = false)
  private UUID authorAccountId;

  @Enumerated(EnumType.STRING)
  @Column(name = "relationship_type", nullable = false, length = 30)
  private RelationshipType relationshipType;

  @Column(name = "residence_from")
  private LocalDate residenceFrom;

  @Column(name = "residence_to")
  private LocalDate residenceTo;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private ReviewStatus status;

  @Column(name = "current_version_id")
  private UUID currentVersionId;

  @Enumerated(EnumType.STRING)
  @Column(name = "verification_tier", nullable = false, length = 30)
  private VerificationTier verificationTier;

  @Column(name = "published_at")
  private Instant publishedAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version private long version;

  protected ReviewJpaEntity() {
    // for JPA
  }

  ReviewJpaEntity(
      UUID id,
      UUID propertyId,
      UUID authorAccountId,
      RelationshipType relationshipType,
      LocalDate residenceFrom,
      LocalDate residenceTo,
      ReviewStatus status,
      UUID currentVersionId,
      VerificationTier verificationTier,
      Instant publishedAt,
      Instant createdAt,
      Instant updatedAt,
      long version) {
    this.id = id;
    this.propertyId = propertyId;
    this.authorAccountId = authorAccountId;
    this.relationshipType = relationshipType;
    this.residenceFrom = residenceFrom;
    this.residenceTo = residenceTo;
    this.status = status;
    this.currentVersionId = currentVersionId;
    this.verificationTier = verificationTier;
    this.publishedAt = publishedAt;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
    this.version = version;
  }

  UUID id() {
    return id;
  }

  UUID propertyId() {
    return propertyId;
  }

  UUID authorAccountId() {
    return authorAccountId;
  }

  RelationshipType relationshipType() {
    return relationshipType;
  }

  LocalDate residenceFrom() {
    return residenceFrom;
  }

  LocalDate residenceTo() {
    return residenceTo;
  }

  ReviewStatus status() {
    return status;
  }

  UUID currentVersionId() {
    return currentVersionId;
  }

  VerificationTier verificationTier() {
    return verificationTier;
  }

  Instant publishedAt() {
    return publishedAt;
  }

  Instant createdAt() {
    return createdAt;
  }

  Instant updatedAt() {
    return updatedAt;
  }

  long version() {
    return version;
  }

  /** Applies a mutated aggregate's lifecycle fields; content rows are appended, never rewritten. */
  void apply(
      ReviewStatus newStatus,
      VerificationTier newVerificationTier,
      UUID newCurrentVersionId,
      Instant newPublishedAt,
      Instant newUpdatedAt) {
    this.status = newStatus;
    this.verificationTier = newVerificationTier;
    this.currentVersionId = newCurrentVersionId;
    this.publishedAt = newPublishedAt;
    this.updatedAt = newUpdatedAt;
  }
}

package com.example.geohousing.reviews.infrastructure.persistence;

import com.example.geohousing.reviews.domain.RelationshipType;
import com.example.geohousing.reviews.domain.ReviewStatus;
import com.example.geohousing.reviews.domain.VerificationTier;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The review aggregate root. Its content versions are a cascaded unidirectional {@code @OneToMany}
 * keyed by {@code review_id}, so saving the root writes the whole graph in one transaction.
 *
 * <p>{@code property_id} and {@code author_account_id} are plain UUID columns, not associations:
 * they point into other modules, which own their own tables.
 *
 * <p>{@code current_version_id} is likewise a plain column rather than a {@code @OneToOne}. The
 * review and its versions reference each other, so one of the two foreign keys has to be checked at
 * commit rather than at insert; {@code V4.2} defers this one, which lets the whole graph be written
 * in a single flush without a follow-up UPDATE that would bump the optimistic-lock version.
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

  @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
  @JoinColumn(name = "review_id", nullable = false)
  @OrderBy("versionNumber")
  private List<ReviewVersionJpaEntity> versions = new ArrayList<>();

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
      long version,
      List<ReviewVersionJpaEntity> versions) {
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
    this.versions = new ArrayList<>(versions);
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

  List<ReviewVersionJpaEntity> versions() {
    return versions;
  }

  /** Applies a mutated aggregate's lifecycle fields; content is appended, never rewritten. */
  void apply(
      ReviewStatus newStatus,
      VerificationTier newVerificationTier,
      Instant newPublishedAt,
      Instant newUpdatedAt,
      List<ReviewVersionJpaEntity> appendedVersions) {
    this.status = newStatus;
    this.verificationTier = newVerificationTier;
    this.publishedAt = newPublishedAt;
    this.updatedAt = newUpdatedAt;
    this.versions.addAll(appendedVersions);
    if (!this.versions.isEmpty()) {
      this.currentVersionId = this.versions.get(this.versions.size() - 1).id();
    }
  }
}

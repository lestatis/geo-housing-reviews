package com.example.geohousing.reviews.infrastructure.persistence;

import com.example.geohousing.reviews.domain.Recommendation;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.BatchSize;

/**
 * One immutable content version of a review, with its category ratings as a cascaded unidirectional
 * {@code @OneToMany}. Nothing here is ever updated after it is written — an edit appends another
 * version. The adapter writes these rows explicitly ({@code review_id} is a plain column, not a
 * mapped association), because the aggregate carries only its current version.
 */
@Entity
@Table(schema = "reviews", name = "review_version")
class ReviewVersionJpaEntity {

  @Id private UUID id;

  @Column(name = "review_id", nullable = false)
  private UUID reviewId;

  @Column(name = "version_number", nullable = false)
  private int versionNumber;

  @Column(nullable = false, length = 10)
  private String locale;

  @Column(nullable = false)
  private String body;

  private String pros;

  private String cons;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private Recommendation recommendation;

  @Column(name = "edit_reason")
  private String editReason;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  /**
   * Batched rather than fetch-joined: a listing already fetch-joins versions, and a second
   * collection fetch join would multiply the rows. {@code @BatchSize} loads the ratings of a whole
   * page of versions in one extra query instead of one per version.
   */
  @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
  @JoinColumn(name = "review_version_id", nullable = false)
  @BatchSize(size = 100)
  private List<CategoryRatingJpaEntity> ratings = new ArrayList<>();

  protected ReviewVersionJpaEntity() {
    // for JPA
  }

  ReviewVersionJpaEntity(
      UUID id,
      UUID reviewId,
      int versionNumber,
      String locale,
      String body,
      String pros,
      String cons,
      Recommendation recommendation,
      String editReason,
      Instant createdAt,
      List<CategoryRatingJpaEntity> ratings) {
    this.id = id;
    this.reviewId = reviewId;
    this.versionNumber = versionNumber;
    this.locale = locale;
    this.body = body;
    this.pros = pros;
    this.cons = cons;
    this.recommendation = recommendation;
    this.editReason = editReason;
    this.createdAt = createdAt;
    this.ratings = new ArrayList<>(ratings);
  }

  UUID id() {
    return id;
  }

  int versionNumber() {
    return versionNumber;
  }

  String locale() {
    return locale;
  }

  String body() {
    return body;
  }

  String pros() {
    return pros;
  }

  String cons() {
    return cons;
  }

  Recommendation recommendation() {
    return recommendation;
  }

  String editReason() {
    return editReason;
  }

  Instant createdAt() {
    return createdAt;
  }

  List<CategoryRatingJpaEntity> ratings() {
    return ratings;
  }
}

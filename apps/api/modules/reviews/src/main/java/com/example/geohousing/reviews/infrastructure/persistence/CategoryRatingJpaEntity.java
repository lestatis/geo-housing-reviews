package com.example.geohousing.reviews.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** One category rating of a review version. */
@Entity
@Table(schema = "reviews", name = "category_rating")
class CategoryRatingJpaEntity {

  @Id private UUID id;

  @Column(nullable = false, length = 50)
  private String category;

  /** {@code SMALLINT} in the schema; null exactly when the category is not applicable. */
  private Short value;

  @Column(name = "not_applicable", nullable = false)
  private boolean notApplicable;

  private String note;

  @Column(name = "category_set_version", nullable = false)
  private int categorySetVersion;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected CategoryRatingJpaEntity() {
    // for JPA
  }

  CategoryRatingJpaEntity(
      UUID id,
      String category,
      Short value,
      boolean notApplicable,
      String note,
      int categorySetVersion,
      Instant createdAt) {
    this.id = id;
    this.category = category;
    this.value = value;
    this.notApplicable = notApplicable;
    this.note = note;
    this.categorySetVersion = categorySetVersion;
    this.createdAt = createdAt;
  }

  String category() {
    return category;
  }

  Short value() {
    return value;
  }

  boolean notApplicable() {
    return notApplicable;
  }

  String note() {
    return note;
  }

  int categorySetVersion() {
    return categorySetVersion;
  }
}

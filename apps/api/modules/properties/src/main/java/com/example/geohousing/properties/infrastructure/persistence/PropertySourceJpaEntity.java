package com.example.geohousing.properties.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "properties", name = "property_source")
class PropertySourceJpaEntity {

  @Id private UUID id;

  @Column(name = "source_type", nullable = false, length = 50)
  private String sourceType;

  @Column(name = "observed_at", nullable = false)
  private Instant observedAt;

  private String note;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected PropertySourceJpaEntity() {}

  PropertySourceJpaEntity(
      UUID id, String sourceType, Instant observedAt, String note, Instant createdAt) {
    this.id = id;
    this.sourceType = sourceType;
    this.observedAt = observedAt;
    this.note = note;
    this.createdAt = createdAt;
  }

  String sourceType() {
    return sourceType;
  }

  Instant observedAt() {
    return observedAt;
  }

  String note() {
    return note;
  }
}

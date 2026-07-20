package com.example.geohousing.properties.infrastructure.persistence;

import com.example.geohousing.properties.domain.AliasSource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "properties", name = "property_alias")
class PropertyAliasJpaEntity {

  @Id private UUID id;

  @Column(nullable = false, length = 10)
  private String locale;

  @Column(nullable = false, length = 300)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private AliasSource source;

  private BigDecimal confidence;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected PropertyAliasJpaEntity() {}

  PropertyAliasJpaEntity(
      UUID id,
      String locale,
      String name,
      AliasSource source,
      BigDecimal confidence,
      Instant createdAt) {
    this.id = id;
    this.locale = locale;
    this.name = name;
    this.source = source;
    this.confidence = confidence;
    this.createdAt = createdAt;
  }

  String locale() {
    return locale;
  }

  String name() {
    return name;
  }

  AliasSource source() {
    return source;
  }

  BigDecimal confidence() {
    return confidence;
  }
}

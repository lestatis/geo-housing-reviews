package com.example.geohousing.properties.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "properties", name = "address")
class AddressJpaEntity {

  @Id private UUID id;

  @Column(nullable = false, length = 2)
  private String country;

  @Column(length = 200)
  private String city;

  @Column(length = 200)
  private String district;

  @Column(length = 300)
  private String street;

  @Column(length = 100)
  private String building;

  @Column(name = "original_text")
  private String originalText;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected AddressJpaEntity() {}

  AddressJpaEntity(
      UUID id,
      String country,
      String city,
      String district,
      String street,
      String building,
      String originalText,
      Instant createdAt) {
    this.id = id;
    this.country = country;
    this.city = city;
    this.district = district;
    this.street = street;
    this.building = building;
    this.originalText = originalText;
    this.createdAt = createdAt;
  }

  String country() {
    return country;
  }

  String city() {
    return city;
  }

  String district() {
    return district;
  }

  String street() {
    return street;
  }

  String building() {
    return building;
  }

  String originalText() {
    return originalText;
  }
}

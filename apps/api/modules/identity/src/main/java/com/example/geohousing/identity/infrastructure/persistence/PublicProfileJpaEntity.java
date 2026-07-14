package com.example.geohousing.identity.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "identity", name = "public_profile")
class PublicProfileJpaEntity {

  @Id
  @Column(name = "account_id")
  private UUID accountId;

  @Column(nullable = false, unique = true, length = 32)
  private String pseudonym;

  @Column(length = 2048)
  private String avatarUrl;

  @Column(nullable = false, length = 10)
  private String locale;

  @Column(nullable = false)
  private Instant createdAt;

  @Column(nullable = false)
  private Instant updatedAt;

  @Version private long version;

  protected PublicProfileJpaEntity() {}

  PublicProfileJpaEntity(
      UUID accountId,
      String pseudonym,
      String avatarUrl,
      String locale,
      Instant createdAt,
      Instant updatedAt,
      long version) {
    this.accountId = accountId;
    this.pseudonym = pseudonym;
    this.avatarUrl = avatarUrl;
    this.locale = locale;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
    this.version = version;
  }

  UUID accountId() {
    return accountId;
  }

  String pseudonym() {
    return pseudonym;
  }

  String avatarUrl() {
    return avatarUrl;
  }

  String locale() {
    return locale;
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

  void apply(String pseudonym, String avatarUrl, String locale, Instant updatedAt) {
    this.pseudonym = pseudonym;
    this.avatarUrl = avatarUrl;
    this.locale = locale;
    this.updatedAt = updatedAt;
  }
}

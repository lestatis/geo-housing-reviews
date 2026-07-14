package com.example.geohousing.identity.infrastructure.persistence;

import com.example.geohousing.identity.domain.AppealStatus;
import com.example.geohousing.identity.domain.RestrictionScope;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "identity", name = "user_restriction")
class UserRestrictionJpaEntity {

  @Id private UUID id;

  @Column(nullable = false)
  private UUID accountId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private RestrictionScope scope;

  @Column(nullable = false)
  private String reason;

  @Column(nullable = false)
  private Instant startAt;

  private Instant endAt;

  private UUID moderatorAccountId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private AppealStatus appealStatus;

  @Column(nullable = false)
  private Instant createdAt;

  protected UserRestrictionJpaEntity() {}

  UserRestrictionJpaEntity(
      UUID id,
      UUID accountId,
      RestrictionScope scope,
      String reason,
      Instant startAt,
      Instant endAt,
      UUID moderatorAccountId,
      AppealStatus appealStatus,
      Instant createdAt) {
    this.id = id;
    this.accountId = accountId;
    this.scope = scope;
    this.reason = reason;
    this.startAt = startAt;
    this.endAt = endAt;
    this.moderatorAccountId = moderatorAccountId;
    this.appealStatus = appealStatus;
    this.createdAt = createdAt;
  }

  UUID id() {
    return id;
  }

  UUID accountId() {
    return accountId;
  }

  RestrictionScope scope() {
    return scope;
  }

  String reason() {
    return reason;
  }

  Instant startAt() {
    return startAt;
  }

  Instant endAt() {
    return endAt;
  }

  UUID moderatorAccountId() {
    return moderatorAccountId;
  }

  AppealStatus appealStatus() {
    return appealStatus;
  }

  Instant createdAt() {
    return createdAt;
  }
}

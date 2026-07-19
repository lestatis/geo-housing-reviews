package com.example.geohousing.identity.infrastructure.persistence;

import com.example.geohousing.identity.domain.SelfServiceRequestType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "identity", name = "self_service_request")
class SelfServiceRequestJpaEntity {

  @Id private UUID id;

  @Column(name = "account_id", nullable = false)
  private UUID accountId;

  @Column(name = "idempotency_key", nullable = false, length = 200)
  private String idempotencyKey;

  @Enumerated(EnumType.STRING)
  @Column(name = "request_type", nullable = false, length = 20)
  private SelfServiceRequestType requestType;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected SelfServiceRequestJpaEntity() {}

  SelfServiceRequestJpaEntity(
      UUID id,
      UUID accountId,
      String idempotencyKey,
      SelfServiceRequestType requestType,
      Instant createdAt) {
    this.id = id;
    this.accountId = accountId;
    this.idempotencyKey = idempotencyKey;
    this.requestType = requestType;
    this.createdAt = createdAt;
  }

  UUID id() {
    return id;
  }

  UUID accountId() {
    return accountId;
  }

  String idempotencyKey() {
    return idempotencyKey;
  }

  SelfServiceRequestType requestType() {
    return requestType;
  }

  Instant createdAt() {
    return createdAt;
  }
}

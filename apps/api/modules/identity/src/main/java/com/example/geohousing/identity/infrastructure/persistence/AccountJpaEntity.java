package com.example.geohousing.identity.infrastructure.persistence;

import com.example.geohousing.identity.domain.AccountRole;
import com.example.geohousing.identity.domain.AccountStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcType;
import org.hibernate.type.descriptor.jdbc.CharJdbcType;

@Entity
@Table(schema = "identity", name = "account")
class AccountJpaEntity {

  @Id private UUID id;

  @JdbcType(CharJdbcType.class)
  @Column(
      name = "auth_subject_hash",
      nullable = false,
      unique = true,
      columnDefinition = "char(64)")
  private String authSubjectHash;

  @Column(length = 320)
  private String email;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private AccountRole role;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private AccountStatus status;

  @Column(nullable = false)
  private Instant createdAt;

  private Instant closedAt;

  @Version private long version;

  protected AccountJpaEntity() {}

  AccountJpaEntity(
      UUID id,
      String authSubjectHash,
      String email,
      AccountRole role,
      AccountStatus status,
      Instant createdAt,
      Instant closedAt,
      long version) {
    this.id = id;
    this.authSubjectHash = authSubjectHash;
    this.email = email;
    this.role = role;
    this.status = status;
    this.createdAt = createdAt;
    this.closedAt = closedAt;
    this.version = version;
  }

  UUID id() {
    return id;
  }

  String authSubjectHash() {
    return authSubjectHash;
  }

  String email() {
    return email;
  }

  AccountRole role() {
    return role;
  }

  AccountStatus status() {
    return status;
  }

  Instant createdAt() {
    return createdAt;
  }

  Instant closedAt() {
    return closedAt;
  }

  long version() {
    return version;
  }

  /**
   * Applies account closure: CLOSED status, the closure timestamp, and a scrubbed email. Mirrors
   * the domain {@code Account.close}; the auth-subject hash is intentionally retained (no
   * resurrection).
   */
  /** Applies a role change decided by the domain. The only mutable field is the role itself. */
  void applyRole(AccountRole role) {
    this.role = role;
  }

  void applyClosure(Instant closedAt) {
    this.status = AccountStatus.CLOSED;
    this.closedAt = closedAt;
    this.email = null;
  }
}

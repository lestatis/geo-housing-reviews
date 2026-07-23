package com.example.geohousing.verification.infrastructure.persistence;

import com.example.geohousing.verification.application.VerificationCaseRepository;
import com.example.geohousing.verification.domain.AccountRef;
import com.example.geohousing.verification.domain.PropertyRef;
import com.example.geohousing.verification.domain.VerificationCase;
import com.example.geohousing.verification.domain.VerificationCaseId;
import com.example.geohousing.verification.domain.VerificationCaseNotFoundException;
import com.example.geohousing.verification.domain.VerificationStatus;
import com.example.geohousing.verification.domain.VerificationVersionConflictException;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** JPA implementation of the {@link VerificationCaseRepository} port. */
@Repository
public class JpaVerificationCaseRepository implements VerificationCaseRepository {

  private final SpringDataVerificationCaseRepository cases;

  public JpaVerificationCaseRepository(SpringDataVerificationCaseRepository cases) {
    this.cases = cases;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<VerificationCase> findById(VerificationCaseId caseId) {
    return cases.findById(caseId.value()).map(VerificationCaseJpaMapper::toDomain);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<VerificationCase> findLiveByAccountAndProperty(
      AccountRef accountRef, PropertyRef propertyRef) {
    return cases
        .findLive(accountRef.value(), propertyRef.value())
        .map(VerificationCaseJpaMapper::toDomain);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<VerificationCase> findLatestByAccountAndProperty(
      AccountRef accountRef, PropertyRef propertyRef) {
    return cases.findLatest(accountRef.value(), propertyRef.value(), Limit.of(1)).stream()
        .findFirst()
        .map(VerificationCaseJpaMapper::toDomain);
  }

  @Override
  @Transactional(readOnly = true)
  public List<VerificationCase> findByStatus(VerificationStatus status, int limit) {
    return cases.findByStatus(status, Limit.of(limit)).stream()
        .map(VerificationCaseJpaMapper::toDomain)
        .toList();
  }

  @Override
  @Transactional
  public void create(VerificationCase verificationCase) {
    cases.save(VerificationCaseJpaMapper.toEntity(verificationCase));
  }

  /**
   * Loads the stored row and applies the aggregate's decision fields to it, refusing a write from a
   * stale version. Loading and applying (rather than merging a detached copy) keeps the immutable
   * fields — account, property, claim, method — beyond the reach of an update.
   */
  @Override
  @Transactional
  public void save(VerificationCase verificationCase) {
    VerificationCaseJpaEntity entity =
        cases
            .findById(verificationCase.id().value())
            .orElseThrow(() -> new VerificationCaseNotFoundException(verificationCase.id()));
    if (entity.version() != verificationCase.version()) {
      throw new VerificationVersionConflictException(
          "verification case " + verificationCase.id().value() + " was modified concurrently");
    }
    entity.apply(
        verificationCase.status(),
        verificationCase.tier(),
        verificationCase.decisionReasonCode().orElse(null),
        verificationCase.verifiedAt().orElse(null),
        verificationCase.validThrough().orElse(null),
        verificationCase.decidedBy().map(id -> id.value()).orElse(null),
        verificationCase.updatedAt());
  }
}

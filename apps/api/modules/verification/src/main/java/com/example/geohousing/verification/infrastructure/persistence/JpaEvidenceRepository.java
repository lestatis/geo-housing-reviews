package com.example.geohousing.verification.infrastructure.persistence;

import com.example.geohousing.verification.application.EvidenceNotFoundException;
import com.example.geohousing.verification.application.EvidenceRepository;
import com.example.geohousing.verification.domain.EvidenceId;
import com.example.geohousing.verification.domain.VerificationCaseId;
import com.example.geohousing.verification.domain.VerificationEvidence;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** JPA implementation of the {@link EvidenceRepository} port. */
@Repository
public class JpaEvidenceRepository implements EvidenceRepository {

  private final SpringDataVerificationEvidenceRepository evidence;

  public JpaEvidenceRepository(SpringDataVerificationEvidenceRepository evidence) {
    this.evidence = evidence;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<VerificationEvidence> findById(EvidenceId evidenceId) {
    return evidence.findById(evidenceId.value()).map(VerificationEvidenceJpaMapper::toDomain);
  }

  @Override
  @Transactional(readOnly = true)
  public List<VerificationEvidence> findByCase(VerificationCaseId caseId) {
    return evidence.findByCase(caseId.value()).stream()
        .map(VerificationEvidenceJpaMapper::toDomain)
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public List<VerificationEvidence> findPastRetention(Instant asOf, int limit) {
    return evidence.findPastRetention(asOf, Limit.of(limit)).stream()
        .map(VerificationEvidenceJpaMapper::toDomain)
        .toList();
  }

  @Override
  @Transactional
  public void create(VerificationEvidence evidenceToStore) {
    evidence.save(VerificationEvidenceJpaMapper.toEntity(evidenceToStore));
  }

  /**
   * Applies the one thing that can change about stored evidence — its deletion time — to the stored
   * row. Everything else was fixed at upload, so loading and stamping (rather than merging a
   * detached copy) keeps the immutable fields structurally out of reach of an update.
   */
  @Override
  @Transactional
  public void save(VerificationEvidence evidenceToSave) {
    VerificationEvidenceJpaEntity entity =
        evidence
            .findById(evidenceToSave.id().value())
            .orElseThrow(() -> new EvidenceNotFoundException(evidenceToSave.id()));
    evidenceToSave.deletedAt().ifPresent(entity::markDeleted);
  }
}

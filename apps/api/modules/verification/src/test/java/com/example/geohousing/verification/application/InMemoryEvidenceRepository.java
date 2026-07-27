package com.example.geohousing.verification.application;

import com.example.geohousing.verification.domain.EvidenceId;
import com.example.geohousing.verification.domain.VerificationCaseId;
import com.example.geohousing.verification.domain.VerificationEvidence;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory {@link EvidenceRepository} for use-case tests. Like a real repository it hands out
 * snapshots, so a mutation that was never saved is never visible to the next read.
 */
final class InMemoryEvidenceRepository implements EvidenceRepository {

  final Map<EvidenceId, VerificationEvidence> byId = new LinkedHashMap<>();

  private static VerificationEvidence snapshot(VerificationEvidence source) {
    return VerificationEvidence.reconstitute(
        source.id(),
        source.caseId(),
        source.storageReference(),
        source.contentType(),
        source.sizeBytes(),
        source.sha256(),
        source.retentionDeadline(),
        source.uploadedAt(),
        source.deletedAt().orElse(null));
  }

  @Override
  public Optional<VerificationEvidence> findById(EvidenceId evidenceId) {
    return Optional.ofNullable(byId.get(evidenceId)).map(InMemoryEvidenceRepository::snapshot);
  }

  @Override
  public List<VerificationEvidence> findByCase(VerificationCaseId caseId) {
    return byId.values().stream()
        .filter(evidence -> evidence.caseId().equals(caseId))
        .sorted(Comparator.comparing(VerificationEvidence::uploadedAt))
        .map(InMemoryEvidenceRepository::snapshot)
        .toList();
  }

  @Override
  public List<VerificationEvidence> findPastRetention(Instant asOf, int limit) {
    return byId.values().stream()
        .filter(evidence -> evidence.isPastRetention(asOf))
        .sorted(Comparator.comparing(VerificationEvidence::retentionDeadline))
        .limit(limit)
        .map(InMemoryEvidenceRepository::snapshot)
        .toList();
  }

  @Override
  public void create(VerificationEvidence evidence) {
    byId.put(evidence.id(), snapshot(evidence));
  }

  @Override
  public void save(VerificationEvidence evidence) {
    byId.put(evidence.id(), snapshot(evidence));
  }
}

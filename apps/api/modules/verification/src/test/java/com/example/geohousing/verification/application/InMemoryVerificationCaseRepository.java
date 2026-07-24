package com.example.geohousing.verification.application;

import com.example.geohousing.verification.domain.AccountRef;
import com.example.geohousing.verification.domain.PropertyRef;
import com.example.geohousing.verification.domain.VerificationCase;
import com.example.geohousing.verification.domain.VerificationCaseId;
import com.example.geohousing.verification.domain.VerificationDecisionAuditEvent;
import com.example.geohousing.verification.domain.VerificationStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory {@link VerificationCaseRepository} for use-case tests. Like a real repository it never
 * hands out the instance it stores: every write snapshots the aggregate and every read
 * reconstitutes a fresh one, so a mutation that was never saved is never visible.
 */
final class InMemoryVerificationCaseRepository implements VerificationCaseRepository {

  final Map<VerificationCaseId, VerificationCase> byId = new LinkedHashMap<>();
  int saveCount;

  private static VerificationCase snapshot(VerificationCase source) {
    return VerificationCase.reconstitute(
        source.id(),
        source.accountRef(),
        source.propertyRef(),
        source.relationshipClaim(),
        source.method(),
        source.status(),
        source.tier(),
        source.decisionReasonCode().orElse(null),
        source.policyVersion(),
        source.verifiedAt().orElse(null),
        source.validThrough().orElse(null),
        source.decidedBy().orElse(null),
        source.createdAt(),
        source.updatedAt(),
        source.version());
  }

  @Override
  public Optional<VerificationCase> findById(VerificationCaseId caseId) {
    return Optional.ofNullable(byId.get(caseId)).map(InMemoryVerificationCaseRepository::snapshot);
  }

  @Override
  public Optional<VerificationCase> findLiveByAccountAndProperty(
      AccountRef accountRef, PropertyRef propertyRef) {
    return byId.values().stream()
        .filter(c -> c.accountRef().equals(accountRef))
        .filter(c -> c.propertyRef().equals(propertyRef))
        .filter(c -> c.status().isLive())
        .findFirst()
        .map(InMemoryVerificationCaseRepository::snapshot);
  }

  @Override
  public Optional<VerificationCase> findLatestByAccountAndProperty(
      AccountRef accountRef, PropertyRef propertyRef) {
    return byId.values().stream()
        .filter(c -> c.accountRef().equals(accountRef))
        .filter(c -> c.propertyRef().equals(propertyRef))
        .max(Comparator.comparing(VerificationCase::createdAt))
        .map(InMemoryVerificationCaseRepository::snapshot);
  }

  @Override
  public List<VerificationCase> findByStatus(VerificationStatus status, int limit) {
    return byId.values().stream()
        .filter(c -> c.status() == status)
        .sorted(Comparator.comparing(VerificationCase::createdAt))
        .limit(limit)
        .map(InMemoryVerificationCaseRepository::snapshot)
        .toList();
  }

  @Override
  public List<VerificationCase> findLapsedApproved(Instant asOf, int limit) {
    return byId.values().stream()
        .filter(c -> c.status() == VerificationStatus.APPROVED)
        .filter(c -> c.validThrough().map(v -> v.isBefore(asOf)).orElse(false))
        .sorted(Comparator.comparing(c -> c.validThrough().orElseThrow()))
        .limit(limit)
        .map(InMemoryVerificationCaseRepository::snapshot)
        .toList();
  }

  @Override
  public void create(VerificationCase verificationCase) {
    byId.put(verificationCase.id(), snapshot(verificationCase));
  }

  @Override
  public void save(VerificationCase verificationCase) {
    byId.put(verificationCase.id(), snapshot(verificationCase));
    saveCount++;
  }

  /** A decision repository that saves the case through this repo and records the audit events. */
  RecordingDecisionRepository decisionRepository() {
    return new RecordingDecisionRepository();
  }

  final class RecordingDecisionRepository implements VerificationDecisionRepository {
    final List<VerificationDecisionAuditEvent> events = new ArrayList<>();

    @Override
    public void applyDecision(
        VerificationCase verificationCase, VerificationDecisionAuditEvent event) {
      save(verificationCase);
      events.add(event);
    }

    @Override
    public void recordAttempt(VerificationDecisionAuditEvent event) {
      events.add(event);
    }
  }
}

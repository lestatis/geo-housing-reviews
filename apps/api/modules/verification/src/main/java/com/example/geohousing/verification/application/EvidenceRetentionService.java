package com.example.geohousing.verification.application;

import com.example.geohousing.verification.domain.EvidenceAccessEvent;
import com.example.geohousing.verification.domain.VerificationEvidence;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Deletes evidence whose retention deadline has passed (docs/SECURITY_PRIVACY.md §4: "deletion jobs
 * with evidence of completion"; TRUST_VERIFICATION.md §6).
 *
 * <p>Order matters: the object is deleted <em>first</em>, then the row is stamped. If the process
 * dies between the two, the next run finds a row still marked undeleted and deletes an object that
 * is already gone — which the store treats as success. The reverse order could leave a row claiming
 * deletion while the document still sits in the bucket, which is precisely the lie this job exists
 * to prevent.
 *
 * <p>Being system-initiated it has no human actor, so its audit rows record none — the one case the
 * {@code V5.2} check allows that.
 */
public final class EvidenceRetentionService {

  private final EvidenceRepository evidenceRepository;
  private final EvidenceAccessAuditRepository accessAudit;
  private final EvidenceStore evidenceStore;
  private final Clock clock;

  public EvidenceRetentionService(
      EvidenceRepository evidenceRepository,
      EvidenceAccessAuditRepository accessAudit,
      EvidenceStore evidenceStore,
      Clock clock) {
    this.evidenceRepository = Objects.requireNonNull(evidenceRepository, "evidenceRepository");
    this.accessAudit = Objects.requireNonNull(accessAudit, "accessAudit");
    this.evidenceStore = Objects.requireNonNull(evidenceStore, "evidenceStore");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  /**
   * Deletes up to {@code limit} lapsed objects and returns how many were deleted. Idempotent: a row
   * stamped by an earlier run is no longer selected.
   */
  public int deleteLapsed(int limit) {
    Instant now = clock.instant();
    List<VerificationEvidence> lapsed = evidenceRepository.findPastRetention(now, limit);

    int deleted = 0;
    for (VerificationEvidence evidence : lapsed) {
      evidenceStore.delete(new EvidenceStorageKey(evidence.storageReference()));
      evidence.markDeleted(clock);
      evidenceRepository.save(evidence);
      accessAudit.record(EvidenceAccessEvent.systemDeletion(evidence.id(), now));
      deleted++;
    }
    return deleted;
  }
}

package com.example.geohousing.verification.application;

import com.example.geohousing.verification.domain.EvidenceAccessEvent;
import com.example.geohousing.verification.domain.EvidenceId;
import com.example.geohousing.verification.domain.VerificationCase;
import com.example.geohousing.verification.domain.VerificationCaseId;
import com.example.geohousing.verification.domain.VerificationCaseNotFoundException;
import com.example.geohousing.verification.domain.VerificationEvidence;
import java.io.InputStream;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Attaches, reads and deletes Tier 2 evidence.
 *
 * <p>Three rules shape everything here (docs/TRUST_VERIFICATION.md §6, docs/SECURITY_PRIVACY.md
 * §1/§4):
 *
 * <ul>
 *   <li>only the owner of a pending document case may attach evidence, and only a moderator may
 *       read it — the owner cannot re-read their own upload, because there is no product reason to
 *       and every read is a disclosure of highly sensitive data;
 *   <li>every read is audited before the bytes are handed over, so a read that happens is a read
 *       that is recorded;
 *   <li>a retention deadline is set at upload, and deleting the object leaves the metadata row as
 *       proof of what was held and that it was let go.
 * </ul>
 */
public final class EvidenceService {

  private final VerificationCaseRepository caseRepository;
  private final EvidenceRepository evidenceRepository;
  private final EvidenceAccessAuditRepository accessAudit;
  private final EvidenceStore evidenceStore;
  private final EvidenceRetentionPolicy retentionPolicy;
  private final long maxUploadBytes;
  private final Clock clock;

  public EvidenceService(
      VerificationCaseRepository caseRepository,
      EvidenceRepository evidenceRepository,
      EvidenceAccessAuditRepository accessAudit,
      EvidenceStore evidenceStore,
      EvidenceRetentionPolicy retentionPolicy,
      long maxUploadBytes,
      Clock clock) {
    this.caseRepository = Objects.requireNonNull(caseRepository, "caseRepository");
    this.evidenceRepository = Objects.requireNonNull(evidenceRepository, "evidenceRepository");
    this.accessAudit = Objects.requireNonNull(accessAudit, "accessAudit");
    this.evidenceStore = Objects.requireNonNull(evidenceStore, "evidenceStore");
    this.retentionPolicy = Objects.requireNonNull(retentionPolicy, "retentionPolicy");
    if (maxUploadBytes <= 0) {
      throw new IllegalArgumentException("maxUploadBytes must be positive");
    }
    this.maxUploadBytes = maxUploadBytes;
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  /**
   * Attaches a document to the caller's own pending case.
   *
   * <p>The content type is checked against the allowlist <em>before</em> anything is stored, and
   * the store enforces the size cap while reading, so an oversized upload never leaves a partial
   * object. The metadata row is written only after the object lands: an orphaned object is
   * sweepable, but a metadata row pointing at nothing would make a moderator's read fail
   * mysteriously.
   *
   * @throws VerificationCaseNotFoundException if the case does not exist or the caller may not see
   *     it
   * @throws VerificationAccessDeniedException if the caller is not the case owner
   * @throws EvidenceNotAcceptedException if the case is not a pending document case
   */
  public VerificationEvidence attach(
      VerificationCaseId caseId,
      VerificationViewer viewer,
      String declaredContentType,
      InputStream content) {
    Objects.requireNonNull(caseId, "caseId");
    Objects.requireNonNull(viewer, "viewer");
    Objects.requireNonNull(content, "content");

    VerificationCase verificationCase =
        VerificationVisibility.requireVisible(caseRepository.findById(caseId), caseId, viewer);
    // Uploading is the owner's act. A moderator who can see the case still cannot put evidence into
    // it — that would let the reviewer manufacture what they then judge.
    if (!viewer.owns(verificationCase.accountRef())) {
      throw new VerificationAccessDeniedException(caseId);
    }
    if (!verificationCase.acceptsEvidence()) {
      throw new EvidenceNotAcceptedException(caseId);
    }

    String contentType = EvidenceContentType.require(declaredContentType);
    EvidenceStorageKey key = EvidenceStorageKey.mint(caseId);
    StoredEvidence stored = evidenceStore.put(key, contentType, content, maxUploadBytes);

    VerificationEvidence evidence =
        VerificationEvidence.record(
            EvidenceId.of(UUID.randomUUID()),
            caseId,
            stored.key().value(),
            stored.contentType(),
            stored.sizeBytes(),
            stored.sha256(),
            retentionPolicy.deadlineFromUpload(clock),
            clock);
    evidenceRepository.create(evidence);
    return evidence;
  }

  /** The evidence attached to a case, for a moderator's queue view. Metadata only — no bytes. */
  public List<VerificationEvidence> listForCase(
      VerificationCaseId caseId, VerificationViewer viewer) {
    Objects.requireNonNull(caseId, "caseId");
    VerificationCase verificationCase =
        VerificationVisibility.requireVisible(caseRepository.findById(caseId), caseId, viewer);
    return evidenceRepository.findByCase(verificationCase.id());
  }

  /**
   * Opens an evidence object for a moderator, recording the access first.
   *
   * <p>Moderators only: reading evidence is a disclosure of the most sensitive data the platform
   * holds, and TRUST_VERIFICATION §6 restricts it to verification moderators. The audit row is
   * written <em>before</em> the stream is returned, so a read cannot happen unrecorded.
   *
   * @throws EvidenceNotFoundException if it does not exist or the caller may not see it
   * @throws EvidenceContentGoneException if the object was already deleted under retention
   */
  public InputStream read(EvidenceId evidenceId, VerificationViewer viewer) {
    Objects.requireNonNull(evidenceId, "evidenceId");
    Objects.requireNonNull(viewer, "viewer");

    VerificationEvidence evidence =
        evidenceRepository
            .findById(evidenceId)
            .orElseThrow(() -> new EvidenceNotFoundException(evidenceId));
    // Not "forbidden": a non-moderator is not told that this evidence exists at all.
    if (!viewer.moderator()) {
      throw new EvidenceNotFoundException(evidenceId);
    }
    if (evidence.isDeleted()) {
      throw new EvidenceContentGoneException(evidenceId);
    }

    accessAudit.record(
        EvidenceAccessEvent.read(evidenceId, viewer.accountRef().value(), clock.instant()));
    return evidenceStore
        .read(new EvidenceStorageKey(evidence.storageReference()))
        .orElseThrow(() -> new EvidenceContentGoneException(evidenceId));
  }

  /**
   * Deletes every object attached to a case and stamps its metadata — used when a case is decided
   * or cancelled, so raw evidence does not linger once it has served its purpose. Idempotent, and
   * audited with the person responsible.
   *
   * @return how many objects were deleted by this call
   */
  public int deleteForCase(VerificationCaseId caseId, UUID actorAccountId) {
    Objects.requireNonNull(caseId, "caseId");
    Objects.requireNonNull(actorAccountId, "actorAccountId");

    int deleted = 0;
    for (VerificationEvidence evidence : evidenceRepository.findByCase(caseId)) {
      if (evidence.isDeleted()) {
        continue;
      }
      evidenceStore.delete(new EvidenceStorageKey(evidence.storageReference()));
      evidence.markDeleted(clock);
      evidenceRepository.save(evidence);
      accessAudit.record(
          EvidenceAccessEvent.deletion(evidence.id(), actorAccountId, clock.instant()));
      deleted++;
    }
    return deleted;
  }
}

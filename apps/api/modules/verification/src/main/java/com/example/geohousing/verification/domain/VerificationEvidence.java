package com.example.geohousing.verification.domain;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The private metadata for one uploaded document backing a Tier 2 verification case (see {@code
 * docs/DOMAIN_MODEL.md} VerificationEvidence). It records <em>about</em> the document — where the
 * bytes are, their type, size and checksum, when they were uploaded, and by when they must be
 * deleted — but never the document itself, which lives in the quarantined store.
 *
 * <p>The bytes leave, the metadata stays: when the retention deadline passes and the object is
 * deleted, this row is marked deleted rather than removed, so the platform keeps proof of what it
 * held and that it let it go (docs/SECURITY_PRIVACY.md §4/§6).
 *
 * <p>The storage reference is an opaque locator into the quarantined store — never a URL, and the
 * minting/validation of its format is a storage concern, so the domain only requires it to be
 * present.
 */
public final class VerificationEvidence {

  private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");

  private final EvidenceId id;
  private final VerificationCaseId caseId;
  private final String storageReference;
  private final String contentType;
  private final long sizeBytes;
  private final String sha256;
  private final Instant retentionDeadline;
  private final Instant uploadedAt;
  private Instant deletedAt;

  private VerificationEvidence(
      EvidenceId id,
      VerificationCaseId caseId,
      String storageReference,
      String contentType,
      long sizeBytes,
      String sha256,
      Instant retentionDeadline,
      Instant uploadedAt,
      Instant deletedAt) {
    this.id = Objects.requireNonNull(id, "id");
    this.caseId = Objects.requireNonNull(caseId, "caseId");
    this.storageReference = requireText(storageReference, "storageReference");
    this.contentType = requireText(contentType, "contentType");
    if (sizeBytes <= 0) {
      throw new IllegalArgumentException("evidence size must be positive");
    }
    this.sizeBytes = sizeBytes;
    this.sha256 = requireSha256(sha256);
    this.retentionDeadline = Objects.requireNonNull(retentionDeadline, "retentionDeadline");
    this.uploadedAt = Objects.requireNonNull(uploadedAt, "uploadedAt");
    this.deletedAt = deletedAt;
  }

  /** Records freshly uploaded evidence, with a retention deadline set at upload. */
  public static VerificationEvidence record(
      EvidenceId id,
      VerificationCaseId caseId,
      String storageReference,
      String contentType,
      long sizeBytes,
      String sha256,
      Instant retentionDeadline,
      Clock clock) {
    return new VerificationEvidence(
        id,
        caseId,
        storageReference,
        contentType,
        sizeBytes,
        sha256,
        retentionDeadline,
        Objects.requireNonNull(clock, "clock").instant(),
        null);
  }

  /** Rebuilds evidence from persisted state. Intended for persistence adapters only. */
  public static VerificationEvidence reconstitute(
      EvidenceId id,
      VerificationCaseId caseId,
      String storageReference,
      String contentType,
      long sizeBytes,
      String sha256,
      Instant retentionDeadline,
      Instant uploadedAt,
      Instant deletedAt) {
    return new VerificationEvidence(
        id,
        caseId,
        storageReference,
        contentType,
        sizeBytes,
        sha256,
        retentionDeadline,
        uploadedAt,
        deletedAt);
  }

  /**
   * Marks the object deleted. Idempotent — a retention sweep must be safe to retry, so re-marking
   * keeps the original deletion time rather than moving it.
   */
  public void markDeleted(Clock clock) {
    if (deletedAt == null) {
      deletedAt = Objects.requireNonNull(clock, "clock").instant();
    }
  }

  public boolean isDeleted() {
    return deletedAt != null;
  }

  /** Whether the object should have been deleted by {@code now} but has not been. */
  public boolean isPastRetention(Instant now) {
    Objects.requireNonNull(now, "now");
    return deletedAt == null && !now.isBefore(retentionDeadline);
  }

  private static String requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return value;
  }

  private static String requireSha256(String value) {
    Objects.requireNonNull(value, "sha256");
    if (!SHA256.matcher(value).matches()) {
      throw new IllegalArgumentException("sha256 must be 64 lowercase hex characters");
    }
    return value;
  }

  public EvidenceId id() {
    return id;
  }

  public VerificationCaseId caseId() {
    return caseId;
  }

  public String storageReference() {
    return storageReference;
  }

  public String contentType() {
    return contentType;
  }

  public long sizeBytes() {
    return sizeBytes;
  }

  public String sha256() {
    return sha256;
  }

  public Instant retentionDeadline() {
    return retentionDeadline;
  }

  public Instant uploadedAt() {
    return uploadedAt;
  }

  public Optional<Instant> deletedAt() {
    return Optional.ofNullable(deletedAt);
  }
}

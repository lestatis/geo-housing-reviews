package com.example.geohousing.verification.application;

import java.io.InputStream;
import java.util.Optional;

/**
 * Outbound port for the quarantined store holding verification evidence (ADR-0008). The application
 * layer stays free of any storage SDK; the adapter talks to S3-compatible object storage.
 *
 * <p>Objects here are the most sensitive data the platform holds (docs/SECURITY_PRIVACY.md §1).
 * Implementations must keep them private — never publicly readable, never at a guessable URL — and
 * must never log their contents.
 */
public interface EvidenceStore {

  /**
   * Stores an object and reports what was written. The store computes the checksum and size from
   * the stream it actually consumed, so the metadata records the bytes that landed rather than what
   * the client claimed.
   *
   * @param sizeLimitBytes refuse anything larger; the stream is not trusted to declare its own size
   */
  StoredEvidence put(
      EvidenceStorageKey key, String contentType, InputStream content, long sizeLimitBytes);

  /**
   * Opens an object for a single authorized read, or empty if it is not there — which is normal
   * after retention deletion, not an error. The caller must close the stream.
   *
   * <p>Every use of this on behalf of a moderator is audited by the application layer; the store
   * itself grants no ambient access.
   */
  Optional<InputStream> read(EvidenceStorageKey key);

  /**
   * Deletes an object. Idempotent: deleting something already gone succeeds, because retention
   * deletion must be safe to retry and must converge (SECURITY_PRIVACY.md §4).
   */
  void delete(EvidenceStorageKey key);

  /** Whether an object is present — used to prove a deletion actually happened. */
  boolean exists(EvidenceStorageKey key);
}

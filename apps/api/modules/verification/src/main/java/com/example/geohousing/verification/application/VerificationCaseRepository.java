package com.example.geohousing.verification.application;

import com.example.geohousing.verification.domain.AccountRef;
import com.example.geohousing.verification.domain.PropertyRef;
import com.example.geohousing.verification.domain.VerificationCase;
import com.example.geohousing.verification.domain.VerificationCaseId;
import com.example.geohousing.verification.domain.VerificationStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Application port for persisting and reading verification cases. */
public interface VerificationCaseRepository {

  Optional<VerificationCase> findById(VerificationCaseId caseId);

  /**
   * The account's case for this property that still occupies the one-live-case slot, if any.
   * Mirrors the partial unique index from {@code V5.1}: a PENDING or APPROVED case is live;
   * rejected/expired/cancelled cases are excluded, so the account can start again.
   */
  Optional<VerificationCase> findLiveByAccountAndProperty(
      AccountRef accountRef, PropertyRef propertyRef);

  /**
   * The account's most recent case for this property, whatever its status — for reading a badge.
   */
  Optional<VerificationCase> findLatestByAccountAndProperty(
      AccountRef accountRef, PropertyRef propertyRef);

  /** Cases in a given status, oldest first — the moderator queue reads {@code PENDING}. */
  List<VerificationCase> findByStatus(VerificationStatus status, int limit);

  /**
   * Approved cases whose badge validity has lapsed by {@code asOf} — the expiry sweep's input.
   * Cases with no {@code validThrough} never lapse.
   */
  List<VerificationCase> findLapsedApproved(Instant asOf, int limit);

  /** Persists a newly opened case. */
  void create(VerificationCase verificationCase);

  /**
   * Persists changes to an existing case.
   *
   * @throws com.example.geohousing.verification.domain.VerificationVersionConflictException if the
   *     stored version no longer matches the one the case was loaded at
   */
  void save(VerificationCase verificationCase);
}

package com.example.geohousing.verification.application;

import com.example.geohousing.verification.domain.AccountRef;
import com.example.geohousing.verification.domain.PropertyRef;
import com.example.geohousing.verification.domain.VerificationCase;
import com.example.geohousing.verification.domain.VerificationCaseId;
import com.example.geohousing.verification.domain.VerificationStatus;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Reads verification cases, applying the visibility rules a viewer is entitled to. */
public final class VerificationQueryService {

  /** Applied when the caller asks for nothing specific. */
  public static final int DEFAULT_QUEUE_SIZE = 20;

  /** Hard cap on the moderator queue page. */
  public static final int MAX_QUEUE_SIZE = 100;

  private final VerificationCaseRepository caseRepository;

  public VerificationQueryService(VerificationCaseRepository caseRepository) {
    this.caseRepository = Objects.requireNonNull(caseRepository, "caseRepository");
  }

  /**
   * A single case, if the viewer is entitled to see it.
   *
   * @throws com.example.geohousing.verification.domain.VerificationCaseNotFoundException if it does
   *     not exist or is not visible to this viewer
   */
  public VerificationCase getById(VerificationCaseId caseId, VerificationViewer viewer) {
    Objects.requireNonNull(caseId, "caseId");
    Objects.requireNonNull(viewer, "viewer");
    return VerificationVisibility.requireVisible(caseRepository.findById(caseId), caseId, viewer);
  }

  /** The account's own latest case for a property, or empty — used to read its badge. */
  public Optional<VerificationCase> findMine(AccountRef accountRef, PropertyRef propertyRef) {
    Objects.requireNonNull(accountRef, "accountRef");
    Objects.requireNonNull(propertyRef, "propertyRef");
    return caseRepository.findLatestByAccountAndProperty(accountRef, propertyRef);
  }

  /**
   * The moderator queue: pending cases, oldest first. Restricted to moderators — a non-moderator
   * viewer is refused rather than shown an empty list, so the restriction is explicit.
   */
  public List<VerificationCase> pendingQueue(VerificationViewer viewer, Integer requestedSize) {
    Objects.requireNonNull(viewer, "viewer");
    if (!viewer.moderator()) {
      throw VerificationAccessDeniedException.moderatorOnly();
    }
    int size = requestedSize == null ? DEFAULT_QUEUE_SIZE : requestedSize;
    return caseRepository.findByStatus(
        VerificationStatus.PENDING, Math.clamp(size, 1, MAX_QUEUE_SIZE));
  }
}

package com.example.geohousing.verification.application;

import com.example.geohousing.verification.domain.EvidenceId;
import com.example.geohousing.verification.domain.VerificationCaseId;
import com.example.geohousing.verification.domain.VerificationEvidence;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Application port for evidence <em>metadata</em>. The bytes live behind {@link EvidenceStore}. */
public interface EvidenceRepository {

  Optional<VerificationEvidence> findById(EvidenceId evidenceId);

  /**
   * All evidence attached to a case, oldest first — what a moderator sees and what a case sweep.
   */
  List<VerificationEvidence> findByCase(VerificationCaseId caseId);

  /**
   * Evidence whose retention deadline has passed and whose object has not yet been deleted — the
   * sweep's input. Mirrors the partial index on {@code deleted_at IS NULL} from {@code V5.2}.
   */
  List<VerificationEvidence> findPastRetention(Instant asOf, int limit);

  void create(VerificationEvidence evidence);

  /** Persists a change to existing metadata — in practice, stamping {@code deleted_at}. */
  void save(VerificationEvidence evidence);
}

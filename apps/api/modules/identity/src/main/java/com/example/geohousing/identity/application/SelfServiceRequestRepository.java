package com.example.geohousing.identity.application;

import com.example.geohousing.identity.domain.AccountId;
import com.example.geohousing.identity.domain.SelfServiceRequest;
import java.util.Optional;

/** Application port for the identity-local data-subject request log (idempotency records). */
public interface SelfServiceRequestRepository {

  Optional<SelfServiceRequest> find(AccountId accountId, String idempotencyKey);

  /**
   * Persists a new request.
   *
   * @throws com.example.geohousing.identity.domain.SelfServiceRequestAlreadyExistsException if a
   *     request with the same account and key was recorded concurrently — the caller re-reads and
   *     resolves it as a replay or a key conflict
   */
  void record(SelfServiceRequest request);
}

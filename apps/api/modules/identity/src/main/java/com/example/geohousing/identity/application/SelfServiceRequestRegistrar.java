package com.example.geohousing.identity.application;

import com.example.geohousing.identity.domain.AccountId;
import com.example.geohousing.identity.domain.SelfServiceRequest;
import com.example.geohousing.identity.domain.SelfServiceRequestAlreadyExistsException;
import com.example.geohousing.identity.domain.SelfServiceRequestType;
import java.time.Clock;
import java.util.Objects;
import java.util.Optional;

/**
 * Registers a self-service request under an idempotency key, shared by export and deletion. Returns
 * whether this is the first time the key is seen or a replay, and rejects a key reused for a
 * different request type. Deletion and export are themselves idempotent, so callers execute the
 * operation regardless of {@link Outcome}; the outcome is informational.
 */
public final class SelfServiceRequestRegistrar {

  /** Whether the key was newly recorded or had already been used for the same request type. */
  public enum Outcome {
    FIRST,
    REPLAY
  }

  private final SelfServiceRequestRepository selfServiceRequestRepository;
  private final Clock clock;

  public SelfServiceRequestRegistrar(
      SelfServiceRequestRepository selfServiceRequestRepository, Clock clock) {
    this.selfServiceRequestRepository =
        Objects.requireNonNull(selfServiceRequestRepository, "selfServiceRequestRepository");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  public Outcome register(AccountId accountId, String idempotencyKey, SelfServiceRequestType type) {
    Objects.requireNonNull(accountId, "accountId");
    Objects.requireNonNull(type, "type");
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      throw new IllegalArgumentException("idempotencyKey must not be blank");
    }

    Optional<SelfServiceRequest> existing =
        selfServiceRequestRepository.find(accountId, idempotencyKey);
    if (existing.isPresent()) {
      return replayOrConflict(existing.get(), type);
    }

    try {
      selfServiceRequestRepository.record(
          SelfServiceRequest.create(accountId, idempotencyKey, type, clock.instant()));
      return Outcome.FIRST;
    } catch (SelfServiceRequestAlreadyExistsException raced) {
      // A concurrent request recorded the same key first. Re-read to classify it.
      SelfServiceRequest winner =
          selfServiceRequestRepository.find(accountId, idempotencyKey).orElseThrow(() -> raced);
      return replayOrConflict(winner, type);
    }
  }

  private static Outcome replayOrConflict(
      SelfServiceRequest existing, SelfServiceRequestType type) {
    if (existing.type() != type) {
      throw new IdempotencyKeyConflictException(
          "idempotency key already used for a " + existing.type() + " request");
    }
    return Outcome.REPLAY;
  }
}

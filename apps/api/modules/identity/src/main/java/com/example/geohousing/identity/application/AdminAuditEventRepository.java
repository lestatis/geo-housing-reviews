package com.example.geohousing.identity.application;

import com.example.geohousing.identity.domain.AdminAuditEvent;

/** Application port for appending to the admin audit trail. Insert-only by contract. */
public interface AdminAuditEventRepository {

  void record(AdminAuditEvent event);

  /**
   * Records an attempt that is about to be refused, in its own transaction.
   *
   * <p>A refusal ends by throwing, which rolls the caller's transaction back — and would take the
   * audit row with it, so the only trace of somebody trying to demote the last administrator or
   * probing for an account that does not exist would be gone. Those are the attempts worth keeping.
   *
   * <p>Deliberately separate from {@link #record}: a successful change and its audit row must
   * commit together, and an attempt that never became a change must survive the rollback. The two
   * requirements are opposite, so they are two methods rather than one with a flag.
   *
   * <p>Defaults to an ordinary write, which is right for any store with no transaction to escape —
   * every in-memory fake, and anything else that cannot do better. The JPA adapter overrides it.
   */
  default void recordRefusedAttempt(AdminAuditEvent event) {
    record(event);
  }
}

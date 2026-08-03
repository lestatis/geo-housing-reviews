package com.example.geohousing.identity.api;

import java.util.UUID;

/**
 * Restricting an account as the consequence of a decision another module made.
 *
 * <p>Moderation can decide {@code RESTRICT_ACCOUNT} about a review, but the account belongs to
 * identity — which owns the restriction rows, the audit trail, and the rules about what a
 * restriction may look like. This is the whole of what moderation may do to an account: place one.
 * Lifting is an administrator's deliberate act through identity's own endpoint, never a side effect
 * somewhere else.
 */
public interface AccountRestraint {

  /**
   * Restricts an account indefinitely, because a moderation decision carries no duration.
   *
   * <p>An administrator lifts it when the account should be allowed back. Choosing an arbitrary
   * window here would be inventing a policy nobody set, and one that quietly expires.
   *
   * <p>Restricting an already-restricted account does nothing rather than failing: the decision's
   * intended outcome — that this account cannot contribute — already holds, and a moderator
   * deciding a second case about the same person should not have that decision refused.
   */
  void restrict(UUID accountId, UUID moderatorAccountId, String reason);
}

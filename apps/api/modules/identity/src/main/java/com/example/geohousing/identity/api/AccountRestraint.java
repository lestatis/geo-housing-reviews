package com.example.geohousing.identity.api;

import java.util.Optional;
import java.util.UUID;

/**
 * Restricting an account as the consequence of a decision another module made.
 *
 * <p>Moderation can decide {@code RESTRICT_ACCOUNT} about a review, but the account belongs to
 * identity — which owns the restriction rows, the audit trail, and the rules about what a
 * restriction may look like. Moderation may place one, and undo the one it placed — nothing wider.
 * An administrator lifting a restriction for their own reasons is still a deliberate act through
 * identity's own endpoint.
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
  Optional<UUID> restrict(UUID accountId, UUID moderatorAccountId, String reason);

  /**
   * Lifts a restriction this module placed, named by the id {@link #restrict} returned.
   *
   * <p>By id, not by account, because a decision does not necessarily own a restriction: {@code
   * restrict} does nothing when one is already in force, so an appeal that lifted "the account's
   * active restriction" could end one an entirely different case placed and free somebody nobody
   * reconsidered.
   *
   * <p>A restriction that has already ended is not an error. An administrator may have lifted it by
   * hand while the appeal was waiting, and the intended outcome already holds — failing here would
   * punish the author for somebody else's tidying.
   */
  void lift(UUID restrictionId, UUID moderatorAccountId);
}

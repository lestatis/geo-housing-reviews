package com.example.geohousing.identity.infrastructure;

import com.example.geohousing.identity.api.AccountRestraint;
import com.example.geohousing.identity.api.AccountRestrictionUseCase;
import com.example.geohousing.identity.application.AlreadyRestrictedException;
import com.example.geohousing.identity.application.RestrictionNotActiveException;
import com.example.geohousing.identity.domain.AccountId;
import com.example.geohousing.identity.domain.RestrictionScope;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Applies the published {@link AccountRestraint} through identity's own restriction service, so a
 * restriction placed by moderation is audited and shaped exactly like one an administrator places
 * by hand.
 */
@Component
class AccountRestraintAdapter implements AccountRestraint {

  private final AccountRestrictionUseCase restrictions;

  AccountRestraintAdapter(AccountRestrictionUseCase restrictions) {
    this.restrictions = Objects.requireNonNull(restrictions, "restrictions");
  }

  @Override
  public Optional<UUID> restrict(UUID accountId, UUID moderatorAccountId, String reason) {
    try {
      return Optional.of(
          restrictions
              .restrict(
                  AccountId.of(moderatorAccountId),
                  AccountId.of(accountId),
                  RestrictionScope.ACCOUNT_WIDE,
                  reason,
                  null)
              .id());
    } catch (AlreadyRestrictedException alreadyInForce) {
      // The decision's intended outcome already holds. Failing here would refuse a moderator's
      // second case about the same person for a reason that is not their problem — and the refusal
      // is already recorded in the audit trail by the service that raised it.
      //
      // Empty, not the existing restriction's id: this decision did not create it, and reversing
      // this decision must not end a restriction another case placed.
      return Optional.empty();
    }
  }

  @Override
  public void lift(UUID restrictionId, UUID moderatorAccountId) {
    try {
      restrictions.liftByRestrictionId(AccountId.of(moderatorAccountId), restrictionId);
    } catch (RestrictionNotActiveException alreadyEnded) {
      // Someone lifted it by hand while the appeal waited. The outcome the reversal wanted is
      // already true, and the attempt is in the audit trail either way.
    }
  }
}

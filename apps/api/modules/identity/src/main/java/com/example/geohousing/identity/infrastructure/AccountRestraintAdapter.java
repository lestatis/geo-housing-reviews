package com.example.geohousing.identity.infrastructure;

import com.example.geohousing.identity.api.AccountRestraint;
import com.example.geohousing.identity.application.AccountRestrictionService;
import com.example.geohousing.identity.application.AlreadyRestrictedException;
import com.example.geohousing.identity.domain.AccountId;
import com.example.geohousing.identity.domain.RestrictionScope;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Applies the published {@link AccountRestraint} through identity's own restriction service, so a
 * restriction placed by moderation is audited and shaped exactly like one an administrator places
 * by hand.
 */
@Component
class AccountRestraintAdapter implements AccountRestraint {

  private final AccountRestrictionService restrictions;

  AccountRestraintAdapter(AccountRestrictionService restrictions) {
    this.restrictions = Objects.requireNonNull(restrictions, "restrictions");
  }

  @Override
  public void restrict(UUID accountId, UUID moderatorAccountId, String reason) {
    try {
      restrictions.restrict(
          AccountId.of(moderatorAccountId),
          AccountId.of(accountId),
          RestrictionScope.ACCOUNT_WIDE,
          reason,
          null);
    } catch (AlreadyRestrictedException alreadyInForce) {
      // The decision's intended outcome already holds. Failing here would refuse a moderator's
      // second case about the same person for a reason that is not their problem — and the refusal
      // is already recorded in the audit trail by the service that raised it.
    }
  }
}

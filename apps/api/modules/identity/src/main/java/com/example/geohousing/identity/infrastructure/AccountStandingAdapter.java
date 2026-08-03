package com.example.geohousing.identity.infrastructure;

import com.example.geohousing.identity.api.AccountStanding;
import com.example.geohousing.identity.application.UserRestrictionRepository;
import com.example.geohousing.identity.domain.AccountId;
import com.example.geohousing.identity.domain.UserRestriction;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Answers the published {@link AccountStanding} question from identity's own restriction rows.
 *
 * <p>The repository narrows to candidate windows at the database; the domain's {@link
 * UserRestriction#isActiveAt} stays the authoritative check, so a restriction that has simply run
 * out stops applying without anything having to notice.
 */
@Component
class AccountStandingAdapter implements AccountStanding {

  private final UserRestrictionRepository restrictions;
  private final Clock clock;

  AccountStandingAdapter(UserRestrictionRepository restrictions, Clock identityClock) {
    this.restrictions = Objects.requireNonNull(restrictions, "restrictions");
    this.clock = Objects.requireNonNull(identityClock, "identityClock");
  }

  @Override
  public boolean isRestricted(UUID accountId) {
    java.time.Instant now = clock.instant();
    return restrictions.findActiveRestrictions(AccountId.of(accountId), now).stream()
        .anyMatch(restriction -> restriction.isActiveAt(now));
  }
}

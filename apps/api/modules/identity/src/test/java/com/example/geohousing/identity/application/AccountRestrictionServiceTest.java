package com.example.geohousing.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.geohousing.identity.domain.Account;
import com.example.geohousing.identity.domain.AccountId;
import com.example.geohousing.identity.domain.AccountNotFoundException;
import com.example.geohousing.identity.domain.AccountRole;
import com.example.geohousing.identity.domain.AdminAuditAction;
import com.example.geohousing.identity.domain.AdminAuditEvent;
import com.example.geohousing.identity.domain.AdminAuditOutcome;
import com.example.geohousing.identity.domain.RestrictionScope;
import com.example.geohousing.identity.domain.UserRestriction;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AccountRestrictionServiceTest {

  private static final Instant NOW = Instant.parse("2026-08-01T10:00:00Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
  private static final Instant IN_A_WEEK = NOW.plusSeconds(7 * 86_400L);
  private static final AccountId MODERATOR = AccountId.of(UUID.randomUUID());
  private static final AccountId TARGET = AccountId.of(UUID.randomUUID());

  private final InMemoryRestrictions restrictions = new InMemoryRestrictions();
  private final RecordingAudit audit = new RecordingAudit();
  private final KnownAccounts accounts = new KnownAccounts(TARGET);
  private final AccountRestrictionService service =
      new AccountRestrictionService(restrictions, accounts, audit, CLOCK);

  @Test
  void aModeratorRestrictsAnAccountAndTheRestrictionIsInForce() {
    UserRestriction placed =
        service.restrict(
            MODERATOR, TARGET, RestrictionScope.ACCOUNT_WIDE, "posting a flat number", IN_A_WEEK);

    assertThat(placed.isActiveAt(NOW)).isTrue();
    assertThat(placed.moderatorAccountId()).contains(MODERATOR);
    assertThat(restrictions.findActiveRestrictions(TARGET, NOW)).hasSize(1);
    assertThat(audit.only().action()).isEqualTo(AdminAuditAction.RESTRICT_ACCOUNT);
    assertThat(audit.only().outcome()).isEqualTo(AdminAuditOutcome.APPLIED);
    assertThat(audit.only().targetAccountId()).contains(TARGET);
  }

  @Test
  void aRestrictionCanBeIndefinite() {
    UserRestriction placed =
        service.restrict(
            MODERATOR, TARGET, RestrictionScope.ACCOUNT_WIDE, "coordinated abuse", null);

    assertThat(placed.endAt()).isEmpty();
    assertThat(placed.isActiveAt(NOW.plusSeconds(86_400L * 3650))).isTrue();
  }

  @Test
  void anAccountIsNotRestrictedTwiceOverInTheSameScope() {
    // Stacking restrictions makes the end date meaningless — whichever ends last silently wins, and
    // an account told "until the 8th" stays restricted past it. Lift the first, or place a longer
    // one deliberately.
    service.restrict(MODERATOR, TARGET, RestrictionScope.ACCOUNT_WIDE, "first", IN_A_WEEK);
    audit.events.clear();

    assertThatThrownBy(
            () ->
                service.restrict(
                    MODERATOR, TARGET, RestrictionScope.ACCOUNT_WIDE, "second", IN_A_WEEK))
        .isInstanceOf(AlreadyRestrictedException.class);

    assertThat(restrictions.findActiveRestrictions(TARGET, NOW)).hasSize(1);
    assertThat(audit.only().outcome()).isEqualTo(AdminAuditOutcome.REFUSED);
  }

  @Test
  void anExpiredRestrictionDoesNotBlockANewOne() {
    UserRestriction first =
        service.restrict(MODERATOR, TARGET, RestrictionScope.ACCOUNT_WIDE, "first", IN_A_WEEK);
    service.lift(MODERATOR, TARGET, first.id());
    audit.events.clear();

    service.restrict(MODERATOR, TARGET, RestrictionScope.ACCOUNT_WIDE, "again", IN_A_WEEK);

    assertThat(audit.only().outcome()).isEqualTo(AdminAuditOutcome.APPLIED);
  }

  @Test
  void restrictingAnUnknownAccountIsRecordedAsAnAttempt() {
    AccountId missing = AccountId.of(UUID.randomUUID());

    assertThatThrownBy(
            () -> service.restrict(MODERATOR, missing, RestrictionScope.ACCOUNT_WIDE, "why", null))
        .isInstanceOf(AccountNotFoundException.class);

    assertThat(audit.only().outcome()).isEqualTo(AdminAuditOutcome.NOT_FOUND);
  }

  @Test
  void liftingEndsTheRestrictionAndKeepsTheRecord() {
    UserRestriction placed =
        service.restrict(MODERATOR, TARGET, RestrictionScope.ACCOUNT_WIDE, "spam", IN_A_WEEK);
    audit.events.clear();

    UserRestriction lifted = service.lift(MODERATOR, TARGET, placed.id());

    assertThat(lifted.isActiveAt(NOW)).isFalse();
    assertThat(lifted.reason()).isEqualTo("spam");
    // Still there, still readable — an appeal needs to see that it happened.
    assertThat(restrictions.findAllFor(TARGET)).hasSize(1);
    assertThat(audit.only().action()).isEqualTo(AdminAuditAction.LIFT_RESTRICTION);
    assertThat(audit.only().outcome()).isEqualTo(AdminAuditOutcome.APPLIED);
  }

  @Test
  void anAlreadyEndedRestrictionIsNotLiftedTwice() {
    // Lifting again would move the end date forward and rewrite when the account regained access.
    UserRestriction placed =
        service.restrict(MODERATOR, TARGET, RestrictionScope.ACCOUNT_WIDE, "spam", IN_A_WEEK);
    service.lift(MODERATOR, TARGET, placed.id());
    audit.events.clear();

    assertThatThrownBy(() -> service.lift(MODERATOR, TARGET, placed.id()))
        .isInstanceOf(RestrictionNotActiveException.class);

    assertThat(audit.only().outcome()).isEqualTo(AdminAuditOutcome.REFUSED);
  }

  @Test
  void liftingSomethingThatIsNotThereIsRecordedAsAnAttempt() {
    assertThatThrownBy(() -> service.lift(MODERATOR, TARGET, UUID.randomUUID()))
        .isInstanceOf(RestrictionNotFoundException.class);

    assertThat(audit.only().outcome()).isEqualTo(AdminAuditOutcome.NOT_FOUND);
  }

  private static final class RecordingAudit implements AdminAuditEventRepository {

    private final List<AdminAuditEvent> events = new ArrayList<>();

    @Override
    public void record(AdminAuditEvent event) {
      events.add(event);
    }

    private AdminAuditEvent only() {
      assertThat(events).hasSize(1);
      return events.getFirst();
    }
  }

  /** Answers only "does this account exist"; the rest of the port is not exercised here. */
  private record KnownAccounts(AccountId known) implements AccountRepository {
    /** Nothing to lock: one thread, one map. The race only exists against a real database. */
    @Override
    public void lockAccount(AccountId accountId) {
      // Deliberately empty.
    }

    /**
     * Nothing to lock: one thread, one map. What the lock defends against is two transactions
     * reading the administrator set at once, which only a real database can stage — see {@code
     * LastAdministratorConcurrencyIntegrationTest}.
     */
    @Override
    public void lockActiveAdministrators() {
      // Deliberately empty.
    }

    @Override
    public Optional<Account> findById(AccountId accountId) {
      return known.equals(accountId)
          ? Optional.of(Account.provision(accountId, "hashed-" + accountId.value(), null, CLOCK))
          : Optional.empty();
    }

    @Override
    public Optional<Account> findByAuthSubjectHash(String authSubjectHash) {
      throw new UnsupportedOperationException("this double is not used for subject lookup");
    }

    @Override
    public Account save(Account account, long expectedVersion) {
      throw new UnsupportedOperationException("this double is not used for account writes");
    }

    @Override
    public long countByRole(AccountRole role) {
      throw new UnsupportedOperationException("this double is not used for role counting");
    }
  }

  private static final class InMemoryRestrictions implements UserRestrictionRepository {

    private final Map<UUID, UserRestriction> stored = new HashMap<>();

    @Override
    public List<UserRestriction> findActiveRestrictions(AccountId accountId, Instant asOf) {
      return stored.values().stream()
          .filter(r -> r.accountId().equals(accountId))
          .filter(r -> r.isActiveAt(asOf))
          .toList();
    }

    @Override
    public List<UserRestriction> findAllFor(AccountId accountId) {
      return stored.values().stream().filter(r -> r.accountId().equals(accountId)).toList();
    }

    @Override
    public Optional<UserRestriction> findById(UUID restrictionId) {
      return Optional.ofNullable(stored.get(restrictionId));
    }

    @Override
    public void create(UserRestriction restriction) {
      stored.put(restriction.id(), restriction);
    }

    @Override
    public void save(UserRestriction restriction) {
      stored.put(restriction.id(), restriction);
    }
  }

  @Test
  void aRestrictionIsLiftedOnlyThroughTheAccountThatHasIt() {
    // A lift addressed to one account must never land on another because a stale or mistyped link
    // put somebody else's identifier in the path. It is silent when it goes wrong: the moderator
    // sees a success and the wrong person walks free of a restriction nobody meant to end.
    AccountId somebodyElse = AccountId.of(UUID.randomUUID());
    UserRestriction theirs =
        service.restrict(MODERATOR, TARGET, RestrictionScope.ACCOUNT_WIDE, "abuse", IN_A_WEEK);

    assertThatThrownBy(() -> service.lift(MODERATOR, somebodyElse, theirs.id()))
        .isInstanceOf(RestrictionNotFoundException.class);
    assertThat(restrictions.findActiveRestrictions(TARGET, NOW))
        .as("the restriction is untouched, because it was never this account's to lift")
        .isNotEmpty();
  }
}

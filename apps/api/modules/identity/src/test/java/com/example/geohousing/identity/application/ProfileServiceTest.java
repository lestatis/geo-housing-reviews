package com.example.geohousing.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.geohousing.identity.domain.Account;
import com.example.geohousing.identity.domain.AccountClosedException;
import com.example.geohousing.identity.domain.AccountId;
import com.example.geohousing.identity.domain.AccountRestrictedException;
import com.example.geohousing.identity.domain.AccountRole;
import com.example.geohousing.identity.domain.AccountStatus;
import com.example.geohousing.identity.domain.AppealStatus;
import com.example.geohousing.identity.domain.OptimisticLockConflictException;
import com.example.geohousing.identity.domain.Pseudonym;
import com.example.geohousing.identity.domain.PseudonymAlreadyInUseException;
import com.example.geohousing.identity.domain.PublicProfile;
import com.example.geohousing.identity.domain.RestrictionScope;
import com.example.geohousing.identity.domain.UserRestriction;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProfileServiceTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-07-14T10:00:00Z"), ZoneOffset.UTC);
  private static final AccountId ACCOUNT_ID = AccountId.of(UUID.randomUUID());

  // Returns nothing, so an unrestricted account is never blocked.
  private static final UserRestrictionRepository NO_RESTRICTIONS = new ActiveOnly(List.of());

  @Test
  void updatesProfileWhenAccountIsActivePseudonymIsFreeAndVersionMatches() {
    Account account = activeAccount();
    PublicProfile profile = profile(3L);
    InMemoryAccountRepository accounts = new InMemoryAccountRepository(account);
    InMemoryProfileRepository profiles = new InMemoryProfileRepository(profile, Set.of());
    ProfileService service = new ProfileService(accounts, profiles, NO_RESTRICTIONS, CLOCK);

    PublicProfile result =
        service.updateProfile(ACCOUNT_ID, new Pseudonym("New name"), null, "ka", 3L);

    assertThat(result.pseudonym().value()).isEqualTo("New name");
    assertThat(result.avatarUrl()).isEmpty();
    assertThat(result.locale()).isEqualTo("ka");
    assertThat(profiles.savedExpectedVersion).isEqualTo(3L);
  }

  @Test
  void rejectsStaleProfileUpdateBeforeMutatingOrSaving() {
    InMemoryProfileRepository profiles = new InMemoryProfileRepository(profile(3L), Set.of());
    ProfileService service =
        new ProfileService(
            new InMemoryAccountRepository(activeAccount()), profiles, NO_RESTRICTIONS, CLOCK);

    assertThatThrownBy(
            () -> service.updateProfile(ACCOUNT_ID, new Pseudonym("New name"), null, "en", 2L))
        .isInstanceOf(OptimisticLockConflictException.class);

    assertThat(profiles.profile.pseudonym().value()).isEqualTo("Reviewer-a1b2");
    assertThat(profiles.savedExpectedVersion).isNull();
  }

  @Test
  void rejectsPseudonymAlreadyUsedByAnotherProfile() {
    InMemoryProfileRepository profiles =
        new InMemoryProfileRepository(profile(3L), Set.of("Already used"));
    ProfileService service =
        new ProfileService(
            new InMemoryAccountRepository(activeAccount()), profiles, NO_RESTRICTIONS, CLOCK);

    assertThatThrownBy(
            () -> service.updateProfile(ACCOUNT_ID, new Pseudonym("Already used"), null, "en", 3L))
        .isInstanceOf(PseudonymAlreadyInUseException.class);

    assertThat(profiles.savedExpectedVersion).isNull();
  }

  @Test
  void rejectsClosedAccountProfileUpdate() {
    Account closed = activeAccount();
    closed.close(CLOCK);
    InMemoryProfileRepository profiles = new InMemoryProfileRepository(profile(3L), Set.of());
    ProfileService service =
        new ProfileService(new InMemoryAccountRepository(closed), profiles, NO_RESTRICTIONS, CLOCK);

    assertThatThrownBy(
            () -> service.updateProfile(ACCOUNT_ID, new Pseudonym("New name"), null, "en", 3L))
        .isInstanceOf(AccountClosedException.class);

    assertThat(profiles.savedExpectedVersion).isNull();
  }

  @Test
  void rejectsProfileUpdateWhileAnActiveRestrictionIsInForce() {
    InMemoryProfileRepository profiles = new InMemoryProfileRepository(profile(3L), Set.of());
    UserRestrictionRepository restrictions =
        new ActiveOnly(List.of(restriction(Instant.parse("2026-07-01T00:00:00Z"), null)));
    ProfileService service =
        new ProfileService(
            new InMemoryAccountRepository(activeAccount()), profiles, restrictions, CLOCK);

    assertThatThrownBy(
            () -> service.updateProfile(ACCOUNT_ID, new Pseudonym("New name"), null, "en", 3L))
        .isInstanceOf(AccountRestrictedException.class);

    assertThat(profiles.savedExpectedVersion).isNull();
  }

  @Test
  void allowsProfileUpdateWhenTheOnlyRestrictionHasExpired() {
    InMemoryProfileRepository profiles = new InMemoryProfileRepository(profile(3L), Set.of());
    // The repository returns the restriction unfiltered; ProfileService must apply isActiveAt and
    // see that it ended before the clock, so the update proceeds.
    UserRestrictionRepository restrictions =
        new ActiveOnly(
            List.of(
                restriction(
                    Instant.parse("2026-06-01T00:00:00Z"), Instant.parse("2026-07-01T00:00:00Z"))));
    ProfileService service =
        new ProfileService(
            new InMemoryAccountRepository(activeAccount()), profiles, restrictions, CLOCK);

    PublicProfile result =
        service.updateProfile(ACCOUNT_ID, new Pseudonym("New name"), null, "en", 3L);

    assertThat(result.pseudonym().value()).isEqualTo("New name");
    assertThat(profiles.savedExpectedVersion).isEqualTo(3L);
  }

  @Test
  void rejectsMissingPseudonymBeforeRepositoryCalls() {
    InMemoryProfileRepository profiles = new InMemoryProfileRepository(profile(3L), Set.of());
    ProfileService service =
        new ProfileService(
            new InMemoryAccountRepository(activeAccount()), profiles, NO_RESTRICTIONS, CLOCK);

    assertThatThrownBy(() -> service.updateProfile(ACCOUNT_ID, null, null, "en", 3L))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("pseudonym");

    assertThat(profiles.savedExpectedVersion).isNull();
  }

  private static Account activeAccount() {
    return Account.reconstitute(
        ACCOUNT_ID,
        "hashed-subject",
        null,
        AccountRole.USER,
        AccountStatus.ACTIVE,
        Instant.parse("2026-07-01T00:00:00Z"),
        null,
        0L);
  }

  private static PublicProfile profile(long version) {
    return PublicProfile.reconstitute(
        ACCOUNT_ID,
        new Pseudonym("Reviewer-a1b2"),
        "https://cdn.example/avatar.png",
        "en",
        Instant.parse("2026-07-01T00:00:00Z"),
        Instant.parse("2026-07-01T00:00:00Z"),
        version);
  }

  private static UserRestriction restriction(Instant startAt, Instant endAt) {
    return UserRestriction.reconstitute(
        UUID.randomUUID(),
        ACCOUNT_ID,
        RestrictionScope.ACCOUNT_WIDE,
        "abuse",
        startAt,
        endAt,
        null,
        AppealStatus.NONE,
        Instant.parse("2026-06-01T00:00:00Z"));
  }

  private static final class InMemoryAccountRepository implements AccountRepository {
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

    private final Map<AccountId, Account> accounts = new HashMap<>();

    private InMemoryAccountRepository(Account account) {
      accounts.put(account.id(), account);
    }

    @Override
    public Optional<Account> findById(AccountId accountId) {
      return Optional.ofNullable(accounts.get(accountId));
    }

    @Override
    public Optional<Account> findByAuthSubjectHash(String authSubjectHash) {
      return accounts.values().stream()
          .filter(account -> account.authSubjectHash().equals(authSubjectHash))
          .findFirst();
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

  private static final class InMemoryProfileRepository implements PublicProfileRepository {

    private final Set<String> takenPseudonyms;
    private PublicProfile profile;
    private Long savedExpectedVersion;

    private InMemoryProfileRepository(PublicProfile profile, Set<String> takenPseudonyms) {
      this.profile = profile;
      this.takenPseudonyms = takenPseudonyms;
    }

    @Override
    public Optional<PublicProfile> findByAccountId(AccountId accountId) {
      return profile.accountId().equals(accountId) ? Optional.of(profile) : Optional.empty();
    }

    @Override
    public boolean isPseudonymInUse(Pseudonym pseudonym) {
      return takenPseudonyms.contains(pseudonym.value());
    }

    @Override
    public PublicProfile save(PublicProfile profile, long expectedVersion) {
      this.profile = profile;
      savedExpectedVersion = expectedVersion;
      return profile;
    }

    @Override
    public java.util.Optional<PublicProfile> findByPseudonym(Pseudonym pseudonym) {
      throw new UnsupportedOperationException("this double is not used for pseudonym lookup");
    }
  }

  /**
   * A restriction store that answers only the active-window question. The port grew write methods
   * for placing and lifting; this test is about what an active restriction stops, so the rest
   * throws rather than pretending to work.
   */
  private record ActiveOnly(List<UserRestriction> active) implements UserRestrictionRepository {

    @Override
    public List<UserRestriction> findActiveRestrictions(AccountId accountId, Instant asOf) {
      return active;
    }

    @Override
    public List<UserRestriction> findAllFor(AccountId accountId) {
      throw new UnsupportedOperationException("this double is not used for restriction history");
    }

    @Override
    public java.util.Optional<UserRestriction> findById(java.util.UUID restrictionId) {
      throw new UnsupportedOperationException("this double is not used for restriction lookup");
    }

    @Override
    public void create(UserRestriction restriction) {
      throw new UnsupportedOperationException("this double is not used for placing restrictions");
    }

    @Override
    public void save(UserRestriction restriction) {
      throw new UnsupportedOperationException("this double is not used for lifting restrictions");
    }
  }
}

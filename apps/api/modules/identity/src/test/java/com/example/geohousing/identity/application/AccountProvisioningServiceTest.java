package com.example.geohousing.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.geohousing.identity.domain.Account;
import com.example.geohousing.identity.domain.AccountClosedException;
import com.example.geohousing.identity.domain.AccountId;
import com.example.geohousing.identity.domain.AuthSubjectAlreadyProvisionedException;
import com.example.geohousing.identity.domain.PublicProfile;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class AccountProvisioningServiceTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-07-14T10:00:00Z"), ZoneOffset.UTC);

  @Test
  void hashesBeforeLookupAndCreatesAccountAndProfileAtomically() {
    InMemoryAccountRepository accounts = new InMemoryAccountRepository();
    InMemoryProfileRepository profiles = new InMemoryProfileRepository(Set.of());
    RecordingProvisioningRepository provisioning = new RecordingProvisioningRepository();
    AtomicReference<String> seenRawSubject = new AtomicReference<>();
    AccountProvisioningService service =
        service(
            accounts,
            profiles,
            provisioning,
            rawSubject -> {
              seenRawSubject.set(rawSubject);
              return "hashed-subject";
            },
            () -> "a1b2");

    Account account = service.provision("issuer|subject-123", "person@example.com", "en");

    assertThat(seenRawSubject).hasValue("issuer|subject-123");
    assertThat(accounts.lastLookupHash).isEqualTo("hashed-subject");
    assertThat(account.authSubjectHash()).isEqualTo("hashed-subject");
    assertThat(provisioning.account).isSameAs(account);
    assertThat(provisioning.profile.accountId()).isEqualTo(account.id());
    assertThat(provisioning.profile.pseudonym().value()).isEqualTo("Reviewer-a1b2");
  }

  @Test
  void returnsExistingActiveAccountWithoutCreatingAnotherProfile() {
    InMemoryAccountRepository accounts = new InMemoryAccountRepository();
    Account existing =
        Account.provision(AccountId.of(java.util.UUID.randomUUID()), "hash", null, CLOCK);
    accounts.byHash.put("hash", existing);
    InMemoryProfileRepository profiles = new InMemoryProfileRepository(Set.of());
    RecordingProvisioningRepository provisioning = new RecordingProvisioningRepository();
    AccountProvisioningService service =
        service(accounts, profiles, provisioning, ignored -> "hash", () -> failSuffixSupplier());

    Account result = service.provision("issuer|subject", null, "en");

    assertThat(result).isSameAs(existing);
    assertThat(provisioning.account).isNull();
  }

  @Test
  void rejectsReprovisioningClosedAccountWithoutAllocatingOrPersisting() {
    InMemoryAccountRepository accounts = new InMemoryAccountRepository();
    Account closed =
        Account.provision(AccountId.of(java.util.UUID.randomUUID()), "hash", null, CLOCK);
    closed.close(CLOCK);
    accounts.byHash.put("hash", closed);
    RecordingProvisioningRepository provisioning = new RecordingProvisioningRepository();
    AccountProvisioningService service =
        service(
            accounts,
            new InMemoryProfileRepository(Set.of()),
            provisioning,
            ignored -> "hash",
            () -> failSuffixSupplier());

    assertThatThrownBy(() -> service.provision("issuer|subject", null, "en"))
        .isInstanceOf(AccountClosedException.class);

    assertThat(provisioning.account).isNull();
  }

  @Test
  void rejectsBlankRawSubjectBeforeHashing() {
    AtomicReference<String> seenRawSubject = new AtomicReference<>();
    AccountProvisioningService service =
        service(
            new InMemoryAccountRepository(),
            new InMemoryProfileRepository(Set.of()),
            new RecordingProvisioningRepository(),
            rawSubject -> {
              seenRawSubject.set(rawSubject);
              return "hash";
            },
            () -> "a1b2");

    assertThatThrownBy(() -> service.provision(" ", null, "en"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("rawAuthSubject must not be blank");

    assertThat(seenRawSubject.get()).isNull();
  }

  @Test
  void rejectsBlankHasherOutputBeforeLookupOrPersistence() {
    InMemoryAccountRepository accounts = new InMemoryAccountRepository();
    RecordingProvisioningRepository provisioning = new RecordingProvisioningRepository();
    AccountProvisioningService service =
        service(
            accounts,
            new InMemoryProfileRepository(Set.of()),
            provisioning,
            ignored -> " ",
            () -> failSuffixSupplier());

    assertThatThrownBy(() -> service.provision("issuer|subject", null, "en"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("authSubjectHasher returned a blank hash");

    assertThat(accounts.lastLookupHash).isNull();
    assertThat(provisioning.account).isNull();
  }

  @Test
  void returnsTheRaceWinnerWhenCreateReportsAConcurrentProvision() {
    InMemoryAccountRepository accounts = new InMemoryAccountRepository();
    Account winner =
        Account.provision(AccountId.of(java.util.UUID.randomUUID()), "hash", null, CLOCK);
    // The concurrent request that lost the unique-constraint race sees no account when it looks up,
    // then create() rejects its insert; on re-read the winner's account is now visible.
    RacingProvisioningRepository provisioning =
        new RacingProvisioningRepository(() -> accounts.byHash.put("hash", winner));
    AccountProvisioningService service =
        service(
            accounts,
            new InMemoryProfileRepository(Set.of()),
            provisioning,
            ignored -> "hash",
            () -> "a1b2");

    Account result = service.provision("issuer|subject", null, "en");

    assertThat(result).isSameAs(winner);
    assertThat(provisioning.attempts).isEqualTo(1);
  }

  @Test
  void propagatesTheRaceExceptionIfTheWinnerCannotBeReread() {
    InMemoryAccountRepository accounts = new InMemoryAccountRepository();
    RacingProvisioningRepository provisioning = new RacingProvisioningRepository(() -> {});
    AccountProvisioningService service =
        service(
            accounts,
            new InMemoryProfileRepository(Set.of()),
            provisioning,
            ignored -> "hash",
            () -> "a1b2");

    assertThatThrownBy(() -> service.provision("issuer|subject", null, "en"))
        .isInstanceOf(AuthSubjectAlreadyProvisionedException.class);
  }

  private static AccountProvisioningService service(
      AccountRepository accounts,
      PublicProfileRepository profiles,
      IdentityProvisioningRepository provisioning,
      AuthSubjectHasher hasher,
      Supplier<String> suffixSupplier) {
    return new AccountProvisioningService(
        accounts, provisioning, hasher, new PseudonymAllocator(profiles, suffixSupplier), CLOCK);
  }

  private static String failSuffixSupplier() {
    throw new AssertionError("suffix allocation should not be called");
  }

  private static final class InMemoryAccountRepository implements AccountRepository {

    private final Map<AccountId, Account> byId = new HashMap<>();
    private final Map<String, Account> byHash = new HashMap<>();
    private String lastLookupHash;

    @Override
    public Optional<Account> findById(AccountId accountId) {
      return Optional.ofNullable(byId.get(accountId));
    }

    @Override
    public Optional<Account> findByAuthSubjectHash(String authSubjectHash) {
      lastLookupHash = authSubjectHash;
      return Optional.ofNullable(byHash.get(authSubjectHash));
    }
  }

  private static final class InMemoryProfileRepository implements PublicProfileRepository {

    private final Set<String> takenPseudonyms;

    private InMemoryProfileRepository(Set<String> takenPseudonyms) {
      this.takenPseudonyms = takenPseudonyms;
    }

    @Override
    public Optional<PublicProfile> findByAccountId(AccountId accountId) {
      return Optional.empty();
    }

    @Override
    public boolean isPseudonymInUse(com.example.geohousing.identity.domain.Pseudonym pseudonym) {
      return takenPseudonyms.contains(pseudonym.value());
    }

    @Override
    public PublicProfile save(PublicProfile profile, long expectedVersion) {
      return profile;
    }
  }

  private static final class RecordingProvisioningRepository
      implements IdentityProvisioningRepository {

    private Account account;
    private PublicProfile profile;

    @Override
    public void create(Account account, PublicProfile profile) {
      this.account = account;
      this.profile = profile;
    }
  }

  /** Rejects every create as a lost auth-subject race, running a side effect first. */
  private static final class RacingProvisioningRepository
      implements IdentityProvisioningRepository {

    private final Runnable onCreate;
    private int attempts;

    private RacingProvisioningRepository(Runnable onCreate) {
      this.onCreate = onCreate;
    }

    @Override
    public void create(Account account, PublicProfile profile) {
      attempts++;
      onCreate.run();
      throw new AuthSubjectAlreadyProvisionedException("concurrent provision");
    }
  }
}

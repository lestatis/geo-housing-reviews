package com.example.geohousing.app.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.geohousing.identity.domain.Account;
import com.example.geohousing.identity.domain.AccountId;
import com.example.geohousing.identity.domain.OptimisticLockConflictException;
import com.example.geohousing.identity.domain.Pseudonym;
import com.example.geohousing.identity.domain.PseudonymAlreadyInUseException;
import com.example.geohousing.identity.domain.PublicProfile;
import com.example.geohousing.identity.infrastructure.persistence.JpaIdentityPersistenceAdapter;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest
class IdentityPersistenceIntegrationTest {

  private static final Instant CREATED_AT = Instant.parse("2026-01-01T00:00:00Z");
  private static final Clock CREATED_CLOCK = Clock.fixed(CREATED_AT, ZoneOffset.UTC);

  @Container @ServiceConnection
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(
          DockerImageName.parse("postgis/postgis:18-3.6").asCompatibleSubstituteFor("postgres"));

  @Autowired private JpaIdentityPersistenceAdapter persistence;

  @Test
  void createsAndReconstitutesAccountAndProfileUsingHashedSubject() {
    AccountId accountId = AccountId.of(UUID.randomUUID());
    String subjectHash = "a".repeat(64);
    Account account =
        Account.provision(accountId, subjectHash, "resident@example.com", CREATED_CLOCK);
    PublicProfile profile =
        PublicProfile.createDefault(accountId, Pseudonym.of("Resident-a1b2"), "en", CREATED_CLOCK);

    persistence.create(account, profile);

    Account storedAccount = persistence.findByAuthSubjectHash(subjectHash).orElseThrow();
    PublicProfile storedProfile = persistence.findByAccountId(accountId).orElseThrow();

    assertThat(storedAccount.id()).isEqualTo(accountId);
    assertThat(storedAccount.authSubjectHash()).isEqualTo(subjectHash);
    assertThat(storedAccount.email()).contains("resident@example.com");
    assertThat(storedProfile.accountId()).isEqualTo(accountId);
    assertThat(storedProfile.pseudonym()).isEqualTo(Pseudonym.of("Resident-a1b2"));
  }

  @Test
  void rejectsStaleProfileUpdates() {
    AccountId accountId = createAccountWithProfile("b", "Resident-b1c2");
    PublicProfile profile = persistence.findByAccountId(accountId).orElseThrow();
    profile.updateProfile(
        Pseudonym.of("Resident-b2c3"),
        null,
        "ka",
        Clock.fixed(Instant.parse("2026-01-02T00:00:00Z"), ZoneOffset.UTC));

    PublicProfile saved = persistence.save(profile, profile.version());

    assertThat(saved.version()).isEqualTo(1);
    assertThatThrownBy(() -> persistence.save(profile, profile.version()))
        .isInstanceOf(OptimisticLockConflictException.class);
  }

  @Test
  void translatesDuplicatePseudonymConstraint() {
    AccountId firstAccountId = createAccountWithProfile("c", "Resident-c1d2");
    createAccountWithProfile("d", "Resident-d1e2");
    PublicProfile profile = persistence.findByAccountId(firstAccountId).orElseThrow();
    profile.updateProfile(
        Pseudonym.of("Resident-d1e2"),
        null,
        "en",
        Clock.fixed(Instant.parse("2026-01-02T00:00:00Z"), ZoneOffset.UTC));

    assertThatThrownBy(() -> persistence.save(profile, profile.version()))
        .isInstanceOf(PseudonymAlreadyInUseException.class);
  }

  private AccountId createAccountWithProfile(String hashCharacter, String pseudonym) {
    AccountId accountId = AccountId.of(UUID.randomUUID());
    Account account =
        Account.provision(
            accountId,
            hashCharacter.repeat(64),
            pseudonym.toLowerCase() + "@example.com",
            CREATED_CLOCK);
    PublicProfile profile =
        PublicProfile.createDefault(accountId, Pseudonym.of(pseudonym), "en", CREATED_CLOCK);
    persistence.create(account, profile);
    return accountId;
  }
}

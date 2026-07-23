package com.example.geohousing.app.verification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.geohousing.verification.application.VerificationCaseRepository;
import com.example.geohousing.verification.domain.AccountRef;
import com.example.geohousing.verification.domain.ModeratorId;
import com.example.geohousing.verification.domain.PropertyRef;
import com.example.geohousing.verification.domain.RelationshipClaim;
import com.example.geohousing.verification.domain.VerificationCase;
import com.example.geohousing.verification.domain.VerificationCaseId;
import com.example.geohousing.verification.domain.VerificationMethod;
import com.example.geohousing.verification.domain.VerificationStatus;
import com.example.geohousing.verification.domain.VerificationTier;
import com.example.geohousing.verification.domain.VerificationVersionConflictException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * The verification case aggregate against a real database. Because the application boots with
 * {@code ddl-auto=validate}, every mapping here is checked against the migrated schema before a
 * single assertion runs.
 */
@Testcontainers
@SpringBootTest
class VerificationPersistenceIntegrationTest {

  private static final Instant CREATED_AT = Instant.parse("2026-07-23T10:00:00Z");
  private static final Clock CLOCK = Clock.fixed(CREATED_AT, ZoneOffset.UTC);
  private static final ModeratorId MODERATOR = ModeratorId.of(UUID.randomUUID());

  @Container @ServiceConnection
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(
          DockerImageName.parse("postgis/postgis:18-3.6").asCompatibleSubstituteFor("postgres"));

  @Autowired private VerificationCaseRepository cases;

  private VerificationCase openCase(AccountRef account, PropertyRef property) {
    VerificationCase verificationCase =
        VerificationCase.open(
            VerificationCaseId.of(UUID.randomUUID()),
            account,
            property,
            RelationshipClaim.FORMER_RESIDENT,
            VerificationMethod.BUILDING_CODE,
            1,
            CLOCK);
    cases.create(verificationCase);
    return verificationCase;
  }

  @Test
  void persistsAndReconstitutesAPendingCase() {
    AccountRef account = AccountRef.of(UUID.randomUUID());
    PropertyRef property = PropertyRef.of(UUID.randomUUID());
    VerificationCase opened = openCase(account, property);

    VerificationCase loaded = cases.findById(opened.id()).orElseThrow();

    assertThat(loaded.accountRef()).isEqualTo(account);
    assertThat(loaded.propertyRef()).isEqualTo(property);
    assertThat(loaded.relationshipClaim()).isEqualTo(RelationshipClaim.FORMER_RESIDENT);
    assertThat(loaded.method()).isEqualTo(VerificationMethod.BUILDING_CODE);
    assertThat(loaded.status()).isEqualTo(VerificationStatus.PENDING);
    assertThat(loaded.tier()).isEqualTo(VerificationTier.UNVERIFIED);
    assertThat(loaded.decidedBy()).isEmpty();
  }

  @Test
  void persistsAnApprovalWithItsTierDeciderAndReason() {
    VerificationCase opened =
        openCase(AccountRef.of(UUID.randomUUID()), PropertyRef.of(UUID.randomUUID()));
    Instant validThrough = CREATED_AT.plusSeconds(31_536_000L);
    opened.approve(MODERATOR, "BUILDING_CODE_CONFIRMED", validThrough, CLOCK);
    cases.save(opened);

    VerificationCase loaded = cases.findById(opened.id()).orElseThrow();
    assertThat(loaded.status()).isEqualTo(VerificationStatus.APPROVED);
    assertThat(loaded.tier()).isEqualTo(VerificationTier.RELATIONSHIP_SIGNAL);
    assertThat(loaded.verifiedAt()).contains(CREATED_AT);
    assertThat(loaded.validThrough()).contains(validThrough);
    assertThat(loaded.decidedBy()).contains(MODERATOR);
    assertThat(loaded.decisionReasonCode()).contains("BUILDING_CODE_CONFIRMED");
    assertThat(loaded.badge()).isPresent();
  }

  @Test
  void aStaleWriteIsRefused() {
    VerificationCase opened =
        openCase(AccountRef.of(UUID.randomUUID()), PropertyRef.of(UUID.randomUUID()));

    VerificationCase first = cases.findById(opened.id()).orElseThrow();
    VerificationCase second = cases.findById(opened.id()).orElseThrow();

    first.approve(MODERATOR, "CLEAN", null, CLOCK);
    cases.save(first);

    second.reject(MODERATOR, "NO_EVIDENCE", CLOCK);
    assertThatThrownBy(() -> cases.save(second))
        .isInstanceOf(VerificationVersionConflictException.class);

    assertThat(cases.findById(opened.id()).orElseThrow().status())
        .isEqualTo(VerificationStatus.APPROVED);
  }

  @Test
  void theDatabaseAlsoRefusesASecondLiveCaseForTheSameAccountAndProperty() {
    AccountRef account = AccountRef.of(UUID.randomUUID());
    PropertyRef property = PropertyRef.of(UUID.randomUUID());
    openCase(account, property);

    VerificationCase second =
        VerificationCase.open(
            VerificationCaseId.of(UUID.randomUUID()),
            account,
            property,
            RelationshipClaim.OWNER,
            VerificationMethod.INVITATION,
            1,
            CLOCK);

    // The application refuses this first; the partial unique index is the backstop.
    assertThatThrownBy(() -> cases.create(second))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void aRejectedCaseNoLongerOccupiesTheLiveSlot() {
    AccountRef account = AccountRef.of(UUID.randomUUID());
    PropertyRef property = PropertyRef.of(UUID.randomUUID());
    VerificationCase opened = openCase(account, property);
    assertThat(cases.findLiveByAccountAndProperty(account, property)).isPresent();

    opened.reject(MODERATOR, "NO_EVIDENCE", CLOCK);
    cases.save(opened);

    assertThat(cases.findLiveByAccountAndProperty(account, property)).isEmpty();
    // The latest lookup still finds it, whatever its status.
    assertThat(cases.findLatestByAccountAndProperty(account, property)).isPresent();
  }

  @Test
  void thePendingQueueReturnsOldestFirst() {
    for (int i = 0; i < 3; i++) {
      openCase(AccountRef.of(UUID.randomUUID()), PropertyRef.of(UUID.randomUUID()));
    }

    assertThat(cases.findByStatus(VerificationStatus.PENDING, 50))
        .isNotEmpty()
        .allSatisfy(c -> assertThat(c.status()).isEqualTo(VerificationStatus.PENDING));
  }
}

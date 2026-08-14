package com.example.geohousing.app.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.geohousing.identity.api.AccountRestrictionUseCase;
import com.example.geohousing.identity.api.AccountRoleUseCase;
import com.example.geohousing.identity.domain.AccountId;
import com.example.geohousing.identity.domain.AccountRole;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * The platform must never be left without an administrator.
 *
 * <p>The rule was enforced by counting administrators and then saving the target, in two separate
 * transactions. Two administrators demoting each other at the same moment both counted two, both
 * saved, and nobody was left who could grant the role back — recoverable only with direct database
 * access.
 *
 * <p><strong>This test must actually race.</strong> A sequential version passes against the broken
 * code and proves nothing, which is worse than no test because it reads as coverage. The latch is
 * what makes it a test: both callers are held until each has read the world, and only then
 * released. Verified by removing the lock and watching this fail.
 */
@Testcontainers
@SpringBootTest
// Its own context, and therefore its own database. This test has to answer a question about the
// whole population — "is anyone left in charge" — so it begins by demoting every administrator.
// Spring caches contexts across classes with identical configuration, which would point this at a
// database another class is using and let that wipe reach its data. The marker property gives this
// class a cache key of its own; without it, the damage lands somewhere else and looks unrelated.
@org.springframework.test.context.TestPropertySource(
    properties = "test.isolation=last-administrator-race")
class LastAdministratorConcurrencyIntegrationTest {

  @Container @ServiceConnection
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(
          DockerImageName.parse("postgis/postgis:18-3.6").asCompatibleSubstituteFor("postgres"));

  @Autowired private AccountRoleUseCase roleUseCase;
  @Autowired private AccountRestrictionUseCase restrictionUseCase;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private org.springframework.context.ApplicationContext context;

  @Test
  void theUseCaseActuallyRunsInATransaction() {
    // The lock is only worth anything if it is held until the save commits. A decorator that was
    // never proxied would leave every unit test passing and this whole defence inert.
    assertThat(org.springframework.aop.support.AopUtils.isAopProxy(roleUseCase))
        .as("the transactional decorator is not proxied, so nothing spans the use case")
        .isTrue();
  }

  @Test
  void twoAdministratorsDemotingEachOtherCannotBothSucceed() throws Exception {
    leaveExactlyTwoAdministrators();
    UUID first = administrator("subject-race-first");
    UUID second = administrator("subject-race-second");

    CountDownLatch bothReady = new CountDownLatch(2);
    CountDownLatch go = new CountDownLatch(1);
    ExecutorService threads = Executors.newFixedThreadPool(2);
    try {
      Future<?> a = threads.submit(demote(second, first, bothReady, go));
      Future<?> b = threads.submit(demote(first, second, bothReady, go));

      assertThat(bothReady.await(10, TimeUnit.SECONDS))
          .as("both callers must be lined up, or this is not a race")
          .isTrue();
      go.countDown();

      // One of them is expected to fail — either refused by the rule or beaten to the row. Which
      // one loses is not the point, and asserting it would make this a test of scheduling.
      settle(a);
      settle(b);
    } finally {
      threads.shutdownNow();
    }

    assertThat(activeAdministrators())
        .as("the platform has locked itself out: nobody can grant the role back")
        .isNotEmpty();
  }

  private Runnable demote(UUID actor, UUID target, CountDownLatch ready, CountDownLatch go) {
    return () -> {
      long version = versionOf(target);
      ready.countDown();
      awaitQuietly(go);
      roleUseCase.changeRole(AccountId.of(actor), AccountId.of(target), AccountRole.USER, version);
    };
  }

  /** Whether a caller succeeded or was refused is scheduling; that it did not hang is not. */
  private static void settle(Future<?> attempt) throws Exception {
    try {
      attempt.get(20, TimeUnit.SECONDS);
    } catch (java.util.concurrent.ExecutionException expectedForOne) {
      assertThat(expectedForOne).hasCauseInstanceOf(RuntimeException.class);
    }
  }

  private static void awaitQuietly(CountDownLatch latch) {
    try {
      latch.await(10, TimeUnit.SECONDS);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(interrupted);
    }
  }

  /**
   * The column is constrained to a 64-character lowercase hex digest, so the fixture supplies one
   * rather than a readable string — the shape of a real credential-derived hash, with no credential
   * behind it.
   */
  private UUID administrator(String subject) {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        "insert into identity.account (id, auth_subject_hash, role, status, created_at, version)"
            + " values (?, ?, 'ADMIN', 'ACTIVE', ?, 0)",
        id,
        syntheticDigest(subject + id),
        java.sql.Timestamp.from(Instant.now()));
    return id;
  }

  private static String syntheticDigest(String seed) {
    try {
      byte[] digest =
          java.security.MessageDigest.getInstance("SHA-256")
              .digest(seed.getBytes(java.nio.charset.StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder(64);
      for (byte b : digest) {
        hex.append(String.format("%02x", b));
      }
      return hex.toString();
    } catch (java.security.NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }

  /**
   * Runs share a database and every earlier scenario leaves administrators behind, so "these two
   * are the last" has to be established rather than hoped for — the same lesson the Playwright
   * suite learned about its last-administrator journey.
   */
  private void leaveExactlyTwoAdministrators() {
    jdbcTemplate.update("update identity.account set role = 'USER' where role = 'ADMIN'");
  }

  private long versionOf(UUID accountId) {
    return jdbcTemplate.queryForObject(
        "select version from identity.account where id = ?", Long.class, accountId);
  }

  private List<UUID> activeAdministrators() {
    return jdbcTemplate.queryForList(
        "select id from identity.account where role = 'ADMIN' and status = 'ACTIVE'", UUID.class);
  }

  @Test
  void aRefusedDemotionIsStillRecorded() {
    // The transaction that makes the lock work also rolls back everything a refusal wrote — and an
    // attempt to demote the last administrator is exactly what the audit log is for. Recording it
    // has to outlive the rollback, or fixing one audit defect creates another and a privileged
    // attempt leaves no trace at all.
    leaveExactlyTwoAdministrators();
    UUID onlyOne = administrator("subject-refused-attempt");
    long before = refusedRoleChanges();

    org.assertj.core.api.Assertions.assertThatThrownBy(
            () ->
                roleUseCase.changeRole(
                    AccountId.of(onlyOne),
                    AccountId.of(onlyOne),
                    AccountRole.USER,
                    versionOf(onlyOne)))
        .isInstanceOf(RuntimeException.class);

    assertThat(refusedRoleChanges())
        .as("the attempt to remove the last administrator left no trace")
        .isEqualTo(before + 1);
  }

  private long refusedRoleChanges() {
    return jdbcTemplate.queryForObject(
        "select count(*) from identity.admin_audit_event where outcome = 'REFUSED'", Long.class);
  }

  @Test
  void twoModeratorsRestrictingTheSameAccountLeaveOneRestriction() throws Exception {
    // "At most one active restriction per scope" was checked and then written, in two transactions.
    // Two moderators acting on the same person at the same moment both found nothing and both wrote
    // one — overlapping restrictions the duration rules cannot describe, and a lift that ends only
    // half of them.
    UUID moderator = administrator("subject-restriction-race-moderator");
    UUID target = administrator("subject-restriction-race-target");

    CountDownLatch bothReady = new CountDownLatch(2);
    CountDownLatch go = new CountDownLatch(1);
    ExecutorService threads = Executors.newFixedThreadPool(2);
    try {
      Future<?> a = threads.submit(restrict(moderator, target, "first", bothReady, go));
      Future<?> b = threads.submit(restrict(moderator, target, "second", bothReady, go));

      assertThat(bothReady.await(10, TimeUnit.SECONDS)).isTrue();
      go.countDown();
      settle(a);
      settle(b);
    } finally {
      threads.shutdownNow();
    }

    assertThat(activeRestrictionsFor(target))
        .as("both moderators wrote a restriction, so lifting one leaves the account still barred")
        .isEqualTo(1);
  }

  private Runnable restrict(
      UUID moderator, UUID target, String reason, CountDownLatch ready, CountDownLatch go) {
    return () -> {
      ready.countDown();
      awaitQuietly(go);
      restrictionUseCase.restrict(
          AccountId.of(moderator),
          AccountId.of(target),
          com.example.geohousing.identity.domain.RestrictionScope.ACCOUNT_WIDE,
          reason,
          null);
    };
  }

  private long activeRestrictionsFor(UUID accountId) {
    return jdbcTemplate.queryForObject(
        "select count(*) from identity.user_restriction"
            + " where account_id = ? and start_at <= now() and (end_at is null or end_at > now())",
        Long.class,
        accountId);
  }

  @Test
  void aRefusedRestrictionIsStillRecorded() {
    // The wrapper that holds the lock also rolls back what a refusal wrote. A duplicate restriction
    // attempt is worth keeping: it is how a pattern of one moderator repeatedly going after one
    // account becomes visible at all.
    UUID moderator = administrator("subject-refused-restriction-moderator");
    UUID target = administrator("subject-refused-restriction-target");
    restrictionUseCase.restrict(
        AccountId.of(moderator),
        AccountId.of(target),
        com.example.geohousing.identity.domain.RestrictionScope.ACCOUNT_WIDE,
        "first",
        null);
    long before = refusedRestrictionAttempts();

    org.assertj.core.api.Assertions.assertThatThrownBy(
            () ->
                restrictionUseCase.restrict(
                    AccountId.of(moderator),
                    AccountId.of(target),
                    com.example.geohousing.identity.domain.RestrictionScope.ACCOUNT_WIDE,
                    "second",
                    null))
        .isInstanceOf(RuntimeException.class);

    assertThat(refusedRestrictionAttempts())
        .as("the duplicate attempt left no trace")
        .isEqualTo(before + 1);
  }

  @Test
  void moderationRestrictsThroughTheSameTransactionalPath() {
    // Moderation's RESTRICT_ACCOUNT reaches identity through AccountRestraintAdapter, which used
    // the bare service and so skipped the lock entirely — the race survived on the path a real
    // sanction takes, while the admin screen was protected. Both must be the same object.
    Object adapter = context.getBean("accountRestraintAdapter");

    assertThat(adapter).isNotNull();
    assertThat(restrictionUseCase)
        .as("the decorated use case is what everything reaching identity must go through")
        .isInstanceOf(com.example.geohousing.identity.api.AccountRestrictionUseCase.class);
    assertThat(org.springframework.aop.support.AopUtils.isAopProxy(restrictionUseCase))
        .as("the restriction decorator is not proxied, so nothing spans its use case")
        .isTrue();
  }

  private long refusedRestrictionAttempts() {
    return jdbcTemplate.queryForObject(
        "select count(*) from identity.admin_audit_event"
            + " where action = 'RESTRICT_ACCOUNT' and outcome = 'REFUSED'",
        Long.class);
  }
}

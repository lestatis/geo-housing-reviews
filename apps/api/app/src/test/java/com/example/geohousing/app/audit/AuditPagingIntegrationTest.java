package com.example.geohousing.app.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Paging the timeline against a real database.
 *
 * <p>The merge's ordering is settled by unit tests; what only PostgreSQL can settle is whether the
 * keyset predicate each adapter pushes into SQL agrees with the order the merge sorts by. It orders
 * {@code uuid} by comparing sixteen bytes unsigned, which is not what {@link UUID#compareTo} does —
 * and a disagreement between the two shows up only as rows quietly missing from a page boundary.
 *
 * <p>Rows are inserted with {@code JdbcTemplate} rather than produced by acting through the API,
 * because the point is a collision: several modules recording at the exact same instant, which no
 * sequence of HTTP calls can be made to do on demand.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@Import(AuditPagingIntegrationTest.StubJwtDecoderConfig.class)
class AuditPagingIntegrationTest {

  @Container @ServiceConnection
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(
          DockerImageName.parse("postgis/postgis:18-3.6").asCompatibleSubstituteFor("postgres"));

  /** More than one page at any limit the screen offers, so a walk has boundaries to get wrong. */
  private static final int IDENTITY_EVENTS = 60;

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbcTemplate;

  @TestConfiguration
  static class StubJwtDecoderConfig {

    @Bean
    JwtDecoder jwtDecoder() {
      return token ->
          Jwt.withTokenValue(token)
              .header("alg", "none")
              .subject(token)
              .issuedAt(Instant.parse("2026-07-15T00:00:00Z"))
              .expiresAt(Instant.parse("2999-01-01T00:00:00Z"))
              .build();
    }
  }

  @Test
  void walkingALongTimelineReadsEveryEntryExactlyOnce() throws Exception {
    String admin = "Bearer subject-paging-admin";
    UUID adminId = UUID.fromString(accountIdOf(admin));
    promoteToAdmin(adminId);

    Instant base = Instant.now().truncatedTo(ChronoUnit.SECONDS).minusSeconds(600);
    for (int index = 0; index < IDENTITY_EVENTS; index++) {
      // Three per second, so the window holds both distinct instants and repeats.
      recordIdentityView(adminId, base.plusSeconds(index / 3));
    }
    // The hard case: two modules recording in the same instant, with ids on either side of the
    // signed/unsigned boundary so a wrong comparison cannot pass by luck.
    Instant collision = base.plusSeconds(IDENTITY_EVENTS);
    UUID highBitSet = UUID.fromString("f1000000-0000-4000-8000-000000000001");
    UUID highBitClear = UUID.fromString("11000000-0000-4000-8000-000000000001");
    recordIdentityView(adminId, collision, highBitSet);
    recordIdentityView(adminId, collision, highBitClear);
    recordPropertyHide(adminId, collision, UUID.fromString("f2000000-0000-4000-8000-000000000002"));

    Instant windowEnd = collision.plusSeconds(1);
    List<String> walked = walk(admin, base.minusSeconds(1), windowEnd, 7);
    List<String> wholeWindow = walk(admin, base.minusSeconds(1), windowEnd, 200);

    assertThat(walked)
        .as("seven at a time reads the same timeline as one page of it")
        .isEqualTo(wholeWindow);
    assertThat(walked).doesNotHaveDuplicates();
    assertThat(walked).hasSize(IDENTITY_EVENTS + 3);
  }

  @Test
  void aFullPageCarriesACursorAndTheLastPageDoesNot() throws Exception {
    String admin = "Bearer subject-paging-cursor";
    UUID adminId = UUID.fromString(accountIdOf(admin));
    promoteToAdmin(adminId);

    Instant base = Instant.now().truncatedTo(ChronoUnit.SECONDS).minusSeconds(1200);
    recordIdentityView(adminId, base);
    recordIdentityView(adminId, base.plusSeconds(1));

    String first =
        timeline(admin, base.minusSeconds(1), base.plusSeconds(2), 1, null)
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.nextCursor").isNotEmpty())
            .andReturn()
            .getResponse()
            .getContentAsString();

    timeline(
            admin,
            base.minusSeconds(1),
            base.plusSeconds(2),
            1,
            JsonPath.read(first, "$.nextCursor"))
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.nextCursor").value(org.hamcrest.Matchers.nullValue()));
  }

  @Test
  void aCursorCarriedOntoADifferentSearchIsRefused() throws Exception {
    // Over HTTP, because this is a URL somebody edits: a paged link is shared or bookmarked, the
    // actor or the dates are changed, and the cursor is still attached. Resuming there returns a
    // fragment of a timeline whose start was never seen, and then says there is no more of it.
    String admin = "Bearer subject-paging-scope";
    UUID adminId = UUID.fromString(accountIdOf(admin));
    promoteToAdmin(adminId);

    Instant base = Instant.now().truncatedTo(ChronoUnit.SECONDS).minusSeconds(1800);
    recordIdentityView(adminId, base);
    recordIdentityView(adminId, base.plusSeconds(1));
    Instant since = base.minusSeconds(1);
    Instant until = base.plusSeconds(2);

    String cursor =
        JsonPath.read(
            timeline(admin, since, until, 1, null).andReturn().getResponse().getContentAsString(),
            "$.nextCursor");

    // Same window, different actor filter.
    mockMvc
        .perform(
            get("/api/admin/audit")
                .param("since", since.toString())
                .param("until", until.toString())
                .param("actor", UUID.randomUUID().toString())
                .param("cursor", cursor)
                .header("Authorization", admin))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors[0].field").value("cursor"))
        .andExpect(jsonPath("$.fieldErrors[0].code").value("NOT_FROM_THIS_QUERY"));

    // Same actor, a different — and still enclosing — window.
    mockMvc
        .perform(
            get("/api/admin/audit")
                .param("since", since.minusSeconds(60).toString())
                .param("until", until.toString())
                .param("cursor", cursor)
                .header("Authorization", admin))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors[0].code").value("NOT_FROM_THIS_QUERY"));

    // And the request it does continue is still answered.
    timeline(admin, since, until, 1, cursor).andExpect(jsonPath("$.items.length()").value(1));
  }

  @Test
  void aCursorThatCannotBeReadIsRefusedRatherThanTreatedAsAFirstPage() throws Exception {
    String admin = "Bearer subject-paging-badcursor";
    promoteToAdmin(UUID.fromString(accountIdOf(admin)));

    mockMvc
        .perform(
            get("/api/admin/audit").param("cursor", "not-a-cursor").header("Authorization", admin))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_AUDIT_QUERY"))
        .andExpect(jsonPath("$.fieldErrors[0].field").value("cursor"));
  }

  /** Follows {@code nextCursor} to the end, returning what was read in order. */
  private List<String> walk(String admin, Instant since, Instant until, int limit)
      throws Exception {
    List<String> seen = new ArrayList<>();
    String cursor = null;
    for (int page = 0; page < 100; page++) {
      String body =
          timeline(admin, since, until, limit, cursor)
              .andReturn()
              .getResponse()
              .getContentAsString();
      List<String> subjects = JsonPath.read(body, "$.items[*].subjectId");
      List<String> at = JsonPath.read(body, "$.items[*].at");
      for (int index = 0; index < subjects.size(); index++) {
        seen.add(at.get(index) + " " + subjects.get(index));
      }
      cursor = read(body);
      if (cursor == null) {
        return seen;
      }
    }
    throw new AssertionError("the walk did not terminate — a cursor is not advancing");
  }

  /** {@code nextCursor} is null on the last page; JsonPath reports a null leaf as absent. */
  private static String read(String body) {
    try {
      return JsonPath.read(body, "$.nextCursor");
    } catch (RuntimeException absent) {
      return null;
    }
  }

  private org.springframework.test.web.servlet.ResultActions timeline(
      String admin, Instant since, Instant until, int limit, String cursor) throws Exception {
    var request =
        get("/api/admin/audit")
            .param("since", since.toString())
            .param("until", until.toString())
            .param("limit", String.valueOf(limit))
            .header("Authorization", admin);
    if (cursor != null) {
      request = request.param("cursor", cursor);
    }
    return mockMvc.perform(request).andExpect(status().isOk());
  }

  private UUID recordIdentityView(UUID adminId, Instant at) {
    return recordIdentityView(adminId, at, UUID.randomUUID());
  }

  private UUID recordIdentityView(UUID adminId, Instant at, UUID id) {
    jdbcTemplate.update(
        "insert into identity.admin_audit_event"
            + " (id, admin_account_id, action, target_account_id, outcome, created_at)"
            + " values (?, ?, 'VIEW_ACCOUNT', ?, 'FOUND', ?)",
        id,
        adminId,
        id,
        java.sql.Timestamp.from(at));
    return id;
  }

  private void recordPropertyHide(UUID adminId, Instant at, UUID id) {
    jdbcTemplate.update(
        "insert into properties.property_admin_audit_event"
            + " (id, admin_account_id, action, property_id, outcome, created_at)"
            + " values (?, ?, 'HIDE', ?, 'APPLIED', ?)",
        id,
        adminId,
        id,
        java.sql.Timestamp.from(at));
  }

  private String accountIdOf(String bearer) throws Exception {
    String body =
        mockMvc
            .perform(get("/api/me").header("Authorization", bearer))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(body, "$.accountId");
  }

  private void promoteToAdmin(UUID accountId) {
    jdbcTemplate.update("update identity.account set role = 'ADMIN' where id = ?", accountId);
  }

  @Test
  void aCursorFromAFilteredTimelineCanBeSentBackAlone() throws Exception {
    // The continuation an API client actually makes: take nextCursor, send it, get the next page.
    // It must keep the filter it was issued under — and the response must say which filter that
    // was, because a caller who never stated one would otherwise have to guess, and a screen that
    // guessed "everyone" would label one administrator's actions as the whole platform's.
    String admin = "Bearer subject-paging-inherit";
    UUID adminId = UUID.fromString(accountIdOf(admin));
    promoteToAdmin(adminId);
    UUID somebodyElse = UUID.fromString(accountIdOf("Bearer subject-paging-other"));

    Instant base = Instant.now().truncatedTo(ChronoUnit.SECONDS).minusSeconds(2400);
    recordIdentityView(adminId, base);
    recordIdentityView(adminId, base.plusSeconds(1));
    recordIdentityViewBy(somebodyElse, base.plusSeconds(2));
    Instant since = base.minusSeconds(1);
    Instant until = base.plusSeconds(3);

    String firstPage =
        mockMvc
            .perform(
                get("/api/admin/audit")
                    .param("since", since.toString())
                    .param("until", until.toString())
                    .param("actor", adminId.toString())
                    .param("limit", "1")
                    .header("Authorization", admin))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.applied.actorAccountId").value(adminId.toString()))
            .andReturn()
            .getResponse()
            .getContentAsString();

    // Only the cursor. No window, no actor.
    mockMvc
        .perform(
            get("/api/admin/audit")
                .param("cursor", JsonPath.read(firstPage, "$.nextCursor").toString())
                .param("limit", "5")
                .header("Authorization", admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.applied.actorAccountId").value(adminId.toString()))
        .andExpect(jsonPath("$.applied.since").exists())
        .andExpect(jsonPath("$.applied.until").exists())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].actorAccountId").value(adminId.toString()));
  }

  private void recordIdentityViewBy(UUID actorId, Instant at) {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        "insert into identity.admin_audit_event"
            + " (id, admin_account_id, action, target_account_id, outcome, created_at)"
            + " values (?, ?, 'VIEW_ACCOUNT', ?, 'FOUND', ?)",
        id,
        actorId,
        id,
        java.sql.Timestamp.from(at));
  }

  @Test
  void anUnfilteredPageSaysSoRatherThanOmittingTheField() throws Exception {
    // Null, not absent: "everyone" is an answer to "whose actions is this", and a strict client
    // reading the contract must find the field there to read it.
    String admin = "Bearer subject-applied-everyone";
    promoteToAdmin(UUID.fromString(accountIdOf(admin)));

    mockMvc
        .perform(get("/api/admin/audit").header("Authorization", admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.applied.actorAccountId").doesNotExist())
        .andExpect(jsonPath("$.applied.since").exists());
  }
}

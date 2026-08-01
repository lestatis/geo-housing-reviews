package com.example.geohousing.app.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * The HTTP vocabulary the step definitions share, lifted from the helpers the JUnit endpoint tests
 * each keep their own copy of ({@code bearer}, {@code accountIdOf}, {@code promoteToAdmin}, {@code
 * createProperty}, {@code submitReview}, {@code publish}).
 *
 * <p>Everything goes through {@link MockMvc} and the real security chain. The one exception is
 * granting an administrator, which writes the role directly because identity exposes no
 * bootstrap-an-admin endpoint — and it verifies the grant took effect rather than assuming it.
 */
public class TestApi {

  private final MockMvc mockMvc;
  private final JdbcTemplate jdbcTemplate;
  private final ScenarioState state;
  private String firstAdministrator;

  public TestApi(MockMvc mockMvc, JdbcTemplate jdbcTemplate, ScenarioState state) {
    this.mockMvc = mockMvc;
    this.jdbcTemplate = jdbcTemplate;
    this.state = state;
  }

  /** Performs a request and records it as the scenario's last result. */
  public MvcResult perform(MockHttpServletRequestBuilder request) throws Exception {
    MvcResult result = mockMvc.perform(request).andReturn();
    state.rememberResult(result);
    return result;
  }

  public MockHttpServletRequestBuilder authorized(
      MockHttpServletRequestBuilder request, String actorName) {
    return request.header("Authorization", state.tokenFor(actorName));
  }

  public MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
    return request.contentType(MediaType.APPLICATION_JSON).content(body);
  }

  public int lastStatus() {
    return state.lastResult().getResponse().getStatus();
  }

  public String lastBody() {
    try {
      return state.lastResult().getResponse().getContentAsString();
    } catch (Exception exception) {
      throw new IllegalStateException("could not read the last response body", exception);
    }
  }

  public <T> T readLast(String jsonPath) {
    return JsonPath.read(lastBody(), jsonPath);
  }

  public boolean lastBodyHas(String jsonPath) {
    try {
      JsonPath.read(lastBody(), jsonPath);
      return true;
    } catch (PathNotFoundException notFound) {
      return false;
    }
  }

  /** A token for a brand-new account. The stub decoder treats the token itself as the subject. */
  public String signIn(String actorName) {
    return "Bearer subject-"
        + actorName.toLowerCase(java.util.Locale.ROOT)
        + "-"
        + UUID.randomUUID();
  }

  public String accountIdOf(String actorName) throws Exception {
    MvcResult result =
        mockMvc
            .perform(get("/api/me").header("Authorization", state.tokenFor(actorName)))
            .andReturn();
    assertThat(result.getResponse().getStatus()).as("signing in %s", actorName).isEqualTo(200);
    return JsonPath.read(result.getResponse().getContentAsString(), "$.accountId");
  }

  /**
   * Grants the moderator role, then proves it is in force through an admin-only endpoint — without
   * that check a scenario would still pass if the grant silently updated nothing.
   *
   * <p>The <em>first</em> administrator in a scenario is written directly, because nothing can
   * grant a role until somebody already holds one and this is where that chicken-and-egg is broken.
   * Every one after that goes through {@code PATCH /api/admin/accounts/{id}/role} like a real
   * operator — so the endpoint is exercised by every scenario that needs two administrators, not
   * only by the ones written to test it.
   */
  public void grantAdministrator(String actorName) throws Exception {
    String accountId = accountIdOf(actorName);
    if (firstAdministrator == null) {
      int updated =
          jdbcTemplate.update(
              "update identity.account set role = 'ADMIN' where id = ?::uuid", accountId);
      assertThat(updated).as("bootstrapping %s as the first administrator", actorName).isEqualTo(1);
      firstAdministrator = actorName;
    } else {
      MvcResult granted =
          mockMvc
              .perform(
                  patch("/api/admin/accounts/" + accountId + "/role")
                      .header("Authorization", state.tokenFor(firstAdministrator))
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(
                          "{\"role\":\"ADMIN\",\"version\":" + accountVersion(accountId) + "}"))
              .andReturn();
      assertThat(granted.getResponse().getStatus())
          .as("granting %s the moderator role", actorName)
          .isEqualTo(200);
    }

    assertThat(
            mockMvc
                .perform(
                    get("/api/admin/accounts/" + accountId)
                        .header("Authorization", state.tokenFor(actorName)))
                .andReturn()
                .getResponse()
                .getStatus())
        .as("%s's moderator role is in force", actorName)
        .isEqualTo(200);
  }

  /**
   * Makes this account the platform's only administrator.
   *
   * <p>Written directly, like the bootstrap above, because it is a statement about the whole
   * population rather than about one account — no endpoint expresses "and nobody else". Scenarios
   * share a database and each leaves its administrators behind, so without this the
   * last-administrator rule has no way to become true.
   */
  public void leaveOnlyAdministrator(String accountId) {
    jdbcTemplate.update(
        "update identity.account set role = 'USER' where role = 'ADMIN' and id <> ?::uuid",
        accountId);
  }

  /** The account version an administrator would see before acting on it. */
  public long accountVersionOf(String actorName) throws Exception {
    return accountVersion(state.accountIdFor(actorName));
  }

  private long accountVersion(String accountId) {
    Long version =
        jdbcTemplate.queryForObject(
            "select version from identity.account where id = ?::uuid", Long.class, accountId);
    return version == null ? 0L : version;
  }

  public String createProperty(String actorName, String canonicalName) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                json(
                        authorized(post("/api/properties"), actorName),
                        "{\"type\":\"BUILDING\",\"canonicalName\":\""
                            + canonicalName
                            + " "
                            + UUID.randomUUID()
                            + "\",\"allowDuplicate\":true}")
                    .accept(MediaType.APPLICATION_JSON))
            .andReturn();
    assertThat(result.getResponse().getStatus())
        .as("creating property %s", canonicalName)
        .isEqualTo(201);
    return JsonPath.read(result.getResponse().getContentAsString(), "$.propertyId");
  }

  public MvcResult submitReview(String actorName, String propertyId, String body) throws Exception {
    return perform(
        json(
            authorized(post("/api/properties/" + propertyId + "/reviews"), actorName),
            "{\"relationshipType\":\"CURRENT_RESIDENT\",\"locale\":\"ka\",\"body\":\""
                + body
                + "\",\"recommendation\":\"RECOMMEND\","
                + "\"ratings\":[{\"category\":\"NOISE\",\"value\":4}]}"));
  }

  public MvcResult editReview(String actorName, String reviewId, String body, String reason)
      throws Exception {
    return perform(
        json(
            authorized(put("/api/reviews/" + reviewId), actorName),
            "{\"locale\":\"ka\",\"body\":\""
                + body
                + "\",\"recommendation\":\"NEUTRAL\","
                + "\"ratings\":[{\"category\":\"NOISE\",\"value\":3}],\"editReason\":\""
                + reason
                + "\"}"));
  }

  public MvcResult moderateReview(
      String actorName, String reviewId, String action, long version, String reasonCode)
      throws Exception {
    return perform(
        json(
            authorized(post("/api/admin/reviews/" + reviewId + "/" + action), actorName),
            "{\"version\":" + version + ",\"reasonCode\":\"" + reasonCode + "\"}"));
  }

  public long verificationCaseVersion(String caseId) {
    Long version =
        jdbcTemplate.queryForObject(
            "select version from verification.verification_case where id = ?::uuid",
            Long.class,
            caseId);
    return version == null ? 0L : version;
  }

  public long reviewVersion(String reviewId) {
    Long version =
        jdbcTemplate.queryForObject(
            "select version from reviews.review where id = ?::uuid", Long.class, reviewId);
    return version == null ? 0L : version;
  }
}

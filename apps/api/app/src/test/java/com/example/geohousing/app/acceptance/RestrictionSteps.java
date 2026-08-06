package com.example.geohousing.app.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.List;

/** Steps about finding an account and stopping it contributing. */
public class RestrictionSteps {

  private final ScenarioState state;
  private final TestApi api;

  public RestrictionSteps(ScenarioState state, TestApi api) {
    this.state = state;
    this.api = api;
  }

  @When("{word} looks up the account behind {word}'s pseudonym")
  public void looksUpByPseudonym(String moderator, String actor) throws Exception {
    api.perform(
        api.authorized(
            get("/api/admin/accounts").param("pseudonym", api.pseudonymOf(actor)), moderator));
  }

  @When("{word} looks up the account behind the pseudonym {string}")
  public void looksUpByLiteralPseudonym(String moderator, String pseudonym) throws Exception {
    api.perform(
        api.authorized(get("/api/admin/accounts").param("pseudonym", pseudonym), moderator));
  }

  @Then("the account found is {word}'s")
  public void theAccountFoundIs(String actor) {
    assertThat(api.<String>readLast("$.accountId")).isEqualTo(state.accountIdFor(actor));
  }

  @When("{word} restricts {word} saying {string}")
  public void restricts(String moderator, String actor, String reason) throws Exception {
    api.perform(
        api.json(
            api.authorized(
                post("/api/admin/accounts/" + state.accountIdFor(actor) + "/restrictions"),
                moderator),
            "{\"scope\":\"ACCOUNT_WIDE\",\"reason\":\"" + reason + "\"}"));
    if (api.lastStatus() == 200) {
      state.rememberRestriction(api.readLast("$.restrictionId"), state.accountIdFor(actor));
    }
  }

  @Given("{word} restricted {word} saying {string}")
  public void restricted(String moderator, String actor, String reason) throws Exception {
    restricts(moderator, actor, reason);
    assertThat(api.lastStatus()).as("restricting %s", actor).isEqualTo(200);
  }

  @When("{word} lifts that restriction")
  public void liftsThatRestriction(String moderator) throws Exception {
    api.perform(
        api.authorized(
            post(
                "/api/admin/accounts/"
                    + state.restrictedAccountId()
                    + "/restrictions/"
                    + state.currentRestrictionId()
                    + "/lift"),
            moderator));
  }

  @When("{word} lifts that restriction through {word}'s account")
  public void liftsThatRestrictionThrough(String moderator, String other) throws Exception {
    // The same restriction id, addressed to somebody else's account. A moderation action must land
    // on the person it names, not on whoever the identifier happens to belong to.
    api.perform(
        api.authorized(
            post(
                "/api/admin/accounts/"
                    + state.accountIdFor(other)
                    + "/restrictions/"
                    + state.currentRestrictionId()
                    + "/lift"),
            moderator));
  }

  @Then("{word} is restricted")
  public void isRestricted(String actor) throws Exception {
    assertThat(activeReasons(actor)).as("%s's active restrictions", actor).isNotEmpty();
  }

  @Then("{word} is not restricted")
  public void isNotRestricted(String actor) throws Exception {
    assertThat(activeReasons(actor)).as("%s's active restrictions", actor).isEmpty();
  }

  /**
   * Reads the restriction history and keeps only what is in force. The history is deliberately kept
   * whole — a lifted restriction stays listed — so "is restricted" has to ask about the window
   * rather than about the row's existence.
   */
  private List<String> activeReasons(String actor) throws Exception {
    api.perform(
        api.authorized(
            get("/api/admin/accounts/" + state.accountIdFor(actor) + "/restrictions"),
            state.anyAdministrator()));
    return api.readLast("$.items[?(@.active == true)].reason");
  }
}

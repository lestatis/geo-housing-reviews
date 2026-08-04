package com.example.geohousing.app.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

/** Steps about whether the queues are being served, and whether appeals overturn decisions. */
public class MetricsSteps {

  private final ScenarioState state;
  private final TestApi api;

  private Integer appealsHeardBefore;

  public MetricsSteps(ScenarioState state, TestApi api) {
    this.state = state;
    this.api = api;
  }

  @When("{word} asks for the metrics")
  public void asksForTheMetrics(String actor) throws Exception {
    api.perform(api.authorized(get("/api/admin/metrics"), actor));
  }

  @Given("{word} asked for the metrics")
  public void askedForTheMetrics(String actor) throws Exception {
    asksForTheMetrics(actor);
    assertThat(api.lastStatus()).as("%s reading the metrics", actor).isEqualTo(200);
    appealsHeardBefore = api.<Integer>readLast("$.appealsHeard");
  }

  @When("{word} asks for the metrics from {string} to {string}")
  public void asksForTheMetricsBetween(String actor, String since, String until) throws Exception {
    api.perform(
        api.authorized(
            get("/api/admin/metrics").param("since", since).param("until", until), actor));
  }

  @Then("at least {int} appeal(s) was/were overturned")
  public void atLeastOneAppealOverturned(int expected) {
    assertThat(api.<Integer>readLast("$.appealsOverturned")).isGreaterThanOrEqualTo(expected);
  }

  @Then("every appeal counted as overturned was also counted as heard")
  public void overturnedIsASubsetOfHeard() {
    // Two separate queries. A wrong predicate in either would otherwise surface as an overturn
    // rate above 100% on a screen rather than as a failure here.
    assertThat(api.<Integer>readLast("$.appealsOverturned"))
        .isLessThanOrEqualTo(api.<Integer>readLast("$.appealsHeard"));
  }

  @Then("the count of appeals heard has not changed")
  public void appealsHeardHasNotChanged() {
    assertThat(appealsHeardBefore)
        .as("the scenario must read the metrics before the appeal for this to mean anything")
        .isNotNull();
    assertThat(api.<Integer>readLast("$.appealsHeard")).isEqualTo(appealsHeardBefore);
  }

  @Then("no account is named in the response")
  public void noAccountIsNamed() {
    String body = api.lastBody();
    assertThat(state.knownAccountIds())
        .as("the scenario must introduce at least one account for this check to mean anything")
        .isNotEmpty();
    for (String accountId : state.knownAccountIds()) {
      assertThat(body)
          .as("account %s must not appear in platform metrics", accountId)
          .doesNotContain(accountId);
    }
  }
}

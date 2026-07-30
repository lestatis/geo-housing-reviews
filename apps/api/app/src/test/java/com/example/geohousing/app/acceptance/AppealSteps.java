package com.example.geohousing.app.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

/** Steps about challenging a decision and having a different moderator hear it. */
public class AppealSteps {

  private final ScenarioState state;
  private final TestApi api;

  public AppealSteps(ScenarioState state, TestApi api) {
    this.state = state;
    this.api = api;
  }

  @When("{word} appeals saying {string}")
  public void appeals(String actor, String text) throws Exception {
    submit(actor, text);
    if (api.lastStatus() == 201) {
      state.rememberAppeal(api.readLast("$.appealId"));
    }
  }

  @Given("{word} appealed saying {string}")
  public void appealed(String actor, String text) throws Exception {
    submit(actor, text);
    assertThat(api.lastStatus()).as("filing an appeal").isEqualTo(201);
    state.rememberAppeal(api.readLast("$.appealId"));
  }

  @When("{word} overturns that appeal explaining {string}")
  public void overturns(String moderator, String explanation) throws Exception {
    decide(moderator, "OVERTURN", explanation);
  }

  @When("{word} upholds that appeal explaining {string}")
  public void upholds(String moderator, String explanation) throws Exception {
    decide(moderator, "UPHOLD", explanation);
  }

  @When("{word} asks for that appeal")
  public void asksForThatAppeal(String actor) throws Exception {
    api.perform(api.authorized(get("/api/appeals/" + state.currentAppealId()), actor));
  }

  @Then("the appeal is awaiting a decision")
  public void theAppealIsAwaitingADecision() {
    assertThat(api.<String>readLast("$.status")).isEqualTo("PENDING");
  }

  @Then("the appeal outcome is {string}")
  public void theAppealOutcomeIs(String outcome) {
    assertThat(api.<String>readLast("$.status")).isEqualTo(outcome);
  }

  private void submit(String actor, String text) throws Exception {
    api.perform(
        api.json(
            api.authorized(post("/api/appeals"), actor),
            "{\"targetType\":\"REVIEW\",\"targetId\":\""
                + state.currentReviewId()
                + "\",\"appealText\":\""
                + text
                + "\"}"));
  }

  private void decide(String moderator, String outcome, String explanation) throws Exception {
    api.perform(
        api.json(
            api.authorized(
                post("/api/admin/moderation/appeals/" + state.currentAppealId() + "/decide"),
                moderator),
            "{\"outcome\":\"" + outcome + "\",\"explanation\":\"" + explanation + "\"}"));
  }
}

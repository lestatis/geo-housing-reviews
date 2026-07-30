package com.example.geohousing.app.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

/** Steps about running the platform through its administration interfaces. */
public class ModerationSteps {

  private final ScenarioState state;
  private final TestApi api;

  public ModerationSteps(ScenarioState state, TestApi api) {
    this.state = state;
    this.api = api;
  }

  @When("{word} publishes that review with reason {string}")
  public void publishesThatReview(String moderator, String reasonCode) throws Exception {
    moderate(moderator, "publish", reasonCode);
  }

  @When("{word} hides that review with reason {string}")
  public void hidesThatReview(String moderator, String reasonCode) throws Exception {
    moderate(moderator, "hide", reasonCode);
  }

  @When("{word} rejects that review with reason {string}")
  public void rejectsThatReview(String moderator, String reasonCode) throws Exception {
    moderate(moderator, "reject", reasonCode);
  }

  @When("{word} asks for the moderation queue")
  public void asksForTheModerationQueue(String actor) throws Exception {
    api.perform(api.authorized(get("/api/admin/moderation/cases"), actor));
  }

  @When("{word} opens that case")
  public void opensThatCase(String actor) throws Exception {
    api.perform(api.authorized(get("/api/admin/moderation/cases/" + currentCaseId(actor)), actor));
  }

  @When("{word} decides that case as {string} for {string} explaining {string}")
  public void decidesThatCase(String actor, String action, String reasonCode, String explanation)
      throws Exception {
    api.perform(
        api.json(
            api.authorized(
                post("/api/admin/moderation/cases/" + currentCaseId(actor) + "/decide"), actor),
            "{\"action\":\""
                + action
                + "\",\"reasonCode\":\""
                + reasonCode
                + "\",\"publicExplanation\":\""
                + explanation
                + "\"}"));
  }

  @Then("the queue holds {int} case(s) awaiting a moderator")
  public void theQueueHolds(int expected) {
    assertThat(api.<Integer>readLast("$.items.length()")).isEqualTo(expected);
  }

  @Then("the case shows {int} concern(s)")
  public void theCaseShowsConcerns(int expected) {
    assertThat(api.<Integer>readLast("$.concerns.length()")).isEqualTo(expected);
    assertThat(api.<Integer>readLast("$.summary.concernCount")).isEqualTo(expected);
  }

  @Then("no reporter identity appears in the response")
  public void noReporterIdentityAppears() {
    String body = api.lastBody();
    // A moderator judges the content against the concerns raised. Who raised them cannot make the
    // content more or less acceptable, and carrying identities here would put them one mistake
    // away from the reported author.
    assertThat(state.knownAccountIds())
        .as("the scenario must have introduced accounts for this check to mean anything")
        .isNotEmpty();
    for (String accountId : state.knownAccountIds()) {
      assertThat(body).doesNotContain(accountId);
    }
    assertThat(body).doesNotContain("reporter");
  }

  /** Finds the single live case for the review the scenario is about. */
  private String currentCaseId(String actor) throws Exception {
    api.perform(api.authorized(get("/api/admin/moderation/cases"), actor));
    // A JsonPath filter yields a list; indexing inside the expression does not narrow it.
    java.util.List<String> matches =
        api.readLast("$.items[?(@.targetId=='" + state.currentReviewId() + "')].caseId");
    assertThat(matches).as("a live case for the review this scenario is about").hasSize(1);
    return matches.getFirst();
  }

  private void moderate(String moderator, String action, String reasonCode) throws Exception {
    String reviewId = state.currentReviewId();
    api.moderateReview(moderator, reviewId, action, api.reviewVersion(reviewId), reasonCode);
  }
}

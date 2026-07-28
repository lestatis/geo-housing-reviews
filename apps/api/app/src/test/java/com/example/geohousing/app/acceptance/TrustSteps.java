package com.example.geohousing.app.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

/** Steps about verification: the relationship signal, and what it does to a review's tier. */
public class TrustSteps {

  private final ScenarioState state;
  private final TestApi api;

  public TrustSteps(ScenarioState state, TestApi api) {
    this.state = state;
    this.api = api;
  }

  @Given("{word} opened a verification case for {string}")
  public void openedAVerificationCase(String actor, String propertyName) throws Exception {
    api.perform(
        api.json(
            api.authorized(post("/api/verifications"), actor),
            "{\"propertyId\":\""
                + state.propertyIdFor(propertyName)
                + "\",\"relationshipClaim\":\"CURRENT_RESIDENT\",\"method\":\"BUILDING_CODE\"}"));
    assertThat(api.lastStatus()).as("opening a verification case").isEqualTo(201);
    state.rememberVerificationCase(api.readLast("$.caseId"));
  }

  @When("{word} approves the verification with reason {string}")
  public void approvesTheVerification(String moderator, String reasonCode) throws Exception {
    decide(moderator, "approve", reasonCode);
  }

  @When("{word} revokes the verification with reason {string}")
  public void revokesTheVerification(String moderator, String reasonCode) throws Exception {
    decide(moderator, "revoke", reasonCode);
  }

  @When("{word} asks for that verification case")
  public void asksForThatVerificationCase(String actor) throws Exception {
    api.perform(
        api.authorized(get("/api/verifications/" + state.currentVerificationCaseId()), actor));
  }

  @When("{word} asks for the verification queue")
  public void asksForTheVerificationQueue(String actor) throws Exception {
    api.perform(api.authorized(get("/api/admin/verifications"), actor));
  }

  @Then("that review's verification tier is {string}")
  public void thatReviewsVerificationTierIs(String tier) throws Exception {
    // Read back through the public endpoint: the point is what a reader sees, not what a table
    // says.
    api.perform(get("/api/reviews/" + state.currentReviewId()));
    assertThat(api.lastStatus()).as("reading the review back").isEqualTo(200);
    assertThat(api.<String>readLast("$.verificationTier")).isEqualTo(tier);
  }

  private void decide(String moderator, String action, String reasonCode) throws Exception {
    String caseId = state.currentVerificationCaseId();
    api.perform(
        api.json(
            api.authorized(post("/api/admin/verifications/" + caseId + "/" + action), moderator),
            "{\"version\":"
                + api.verificationCaseVersion(caseId)
                + ",\"reasonCode\":\""
                + reasonCode
                + "\"}"));
  }
}

package com.example.geohousing.app.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.en.Then;

/**
 * What the last request answered.
 *
 * <p>Outcomes are phrased by meaning rather than by status code — "the content is reported as not
 * found" is a privacy decision (never confirm that content a caller may not see exists), while "the
 * request is rejected as forbidden" says the caller is known and refused. Writing them as 404 and
 * 403 in the feature file would hide exactly that distinction.
 */
public class OutcomeSteps {

  private final ScenarioState state;
  private final TestApi api;

  public OutcomeSteps(ScenarioState state, TestApi api) {
    this.state = state;
    this.api = api;
  }

  @Then("the request succeeds")
  public void theRequestSucceeds() {
    assertThat(api.lastStatus())
        .as("expected success but got %d: %s", api.lastStatus(), api.lastBody())
        .isBetween(200, 299);
  }

  @Then("the request is rejected as unauthenticated")
  public void rejectedAsUnauthenticated() {
    assertThat(api.lastStatus()).isEqualTo(401);
  }

  @Then("the request is rejected as forbidden")
  public void rejectedAsForbidden() {
    assertThat(api.lastStatus()).isEqualTo(403);
  }

  @Then("the content is reported as not found")
  public void reportedAsNotFound() {
    assertThat(api.lastStatus()).isEqualTo(404);
  }

  @Then("the request is refused as invalid")
  public void refusedAsInvalid() {
    assertThat(api.lastStatus()).isEqualTo(400);
  }

  @Then("the request is refused as a conflict")
  public void refusedAsConflict() {
    assertThat(api.lastStatus()).isEqualTo(409);
  }

  @Then("the response explains the problem with code {string}")
  public void explainsWithCode(String code) {
    assertThat(api.<String>readLast("$.code")).isEqualTo(code);
  }

  @Then("the listing contains {int} review(s)")
  public void listingContains(int expected) {
    assertThat(api.<Integer>readLast("$.items.length()")).isEqualTo(expected);
  }

  @Then("the review is awaiting moderation")
  public void reviewIsAwaitingModeration() {
    assertThat(api.<String>readLast("$.status")).isEqualTo("PENDING_MODERATION");
  }

  @Then("the review is published")
  public void reviewIsPublished() {
    assertThat(api.<String>readLast("$.status")).isEqualTo("PUBLISHED");
  }

  @Then("the review is at version {int}")
  public void reviewIsAtVersion(int expected) {
    assertThat(api.<Integer>readLast("$.content.versionNumber")).isEqualTo(expected);
  }

  @Then("the review shows {int} helpful signal(s)")
  public void reviewShowsHelpfulSignals(int expected) {
    assertThat(api.<Integer>readLast("$.helpfulCount")).isEqualTo(expected);
  }

  @Then("no voter identity appears in the response")
  public void noVoterIdentityAppears() {
    String body = api.lastBody();
    // A count is public; who produced it is not. Naming voters would let anyone reconstruct a
    // campaign, or work out who criticised their landlord.
    assertThat(state.helpfulVoters())
        .as("the scenario must have recorded at least one voter for this check to mean anything")
        .isNotEmpty();
    for (String voter : state.helpfulVoters()) {
      assertThat(body)
          .as("%s's account id must not appear in a public representation", voter)
          .doesNotContain(state.accountIdFor(voter));
    }
    assertThat(body).doesNotContain("voter");
  }
}

package com.example.geohousing.app.acceptance;

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

  private void moderate(String moderator, String action, String reasonCode) throws Exception {
    String reviewId = state.currentReviewId();
    api.moderateReview(moderator, reviewId, action, api.reviewVersion(reviewId), reasonCode);
  }
}

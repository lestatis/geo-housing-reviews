package com.example.geohousing.app.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;
import org.springframework.test.web.servlet.MvcResult;

/** Steps about writing, editing, reading and endorsing reviews. */
public class ReviewSteps {

  private final ScenarioState state;
  private final TestApi api;

  public ReviewSteps(ScenarioState state, TestApi api) {
    this.state = state;
    this.api = api;
  }

  @Given("{word} submitted a review of {string} saying {string}")
  public void submittedAReview(String actor, String propertyName, String body) throws Exception {
    MvcResult result = api.submitReview(actor, state.propertyIdFor(propertyName), body);
    assertThat(result.getResponse().getStatus()).as("submitting a review").isEqualTo(201);
    state.rememberReview(api.readLast("$.reviewId"));
  }

  @When("{word} submits a review of {string} saying {string}")
  public void submitsAReview(String actor, String propertyName, String body) throws Exception {
    api.submitReview(actor, state.propertyIdFor(propertyName), body);
    if (api.lastStatus() == 201) {
      state.rememberReview(api.readLast("$.reviewId"));
    }
  }

  /**
   * Publishing needs a moderator; the scenarios that use this are about something else, so the
   * moderator is arranged here rather than cluttering the feature file with plumbing.
   */
  @Given("{word} published a review of {string} saying {string}")
  public void publishedAReview(String actor, String propertyName, String body) throws Exception {
    submittedAReview(actor, propertyName, body);
    String moderator = "PublishingModerator";
    if (state.accountIdFor(moderator) == null) {
      state.rememberActor(moderator, api.signIn(moderator));
      state.rememberAccountId(moderator, api.accountIdOf(moderator));
      api.grantAdministrator(moderator);
    }
    String reviewId = state.currentReviewId();
    api.moderateReview(moderator, reviewId, "publish", api.reviewVersion(reviewId), "CLEAN");
    assertThat(api.lastStatus()).as("publishing the review").isEqualTo(200);
  }

  @When("{word} edits that review to say {string} because {string}")
  public void editsThatReview(String actor, String body, String reason) throws Exception {
    api.editReview(actor, state.currentReviewId(), body, reason);
  }

  @When("{word} asks for that review")
  public void asksForThatReview(String actor) throws Exception {
    api.perform(api.authorized(get("/api/reviews/" + state.currentReviewId()), actor));
  }

  @When("an anonymous visitor asks for that review")
  public void anonymousAsksForThatReview() throws Exception {
    api.perform(get("/api/reviews/" + state.currentReviewId()));
  }

  @When("an anonymous visitor tries to submit a review of {string}")
  public void anonymousTriesToSubmit(String propertyName) throws Exception {
    api.perform(
        api.json(
            post("/api/properties/" + state.propertyIdFor(propertyName) + "/reviews"),
            "{\"relationshipType\":\"CURRENT_RESIDENT\",\"locale\":\"ka\","
                + "\"body\":\"ანონიმური\",\"recommendation\":\"RECOMMEND\","
                + "\"ratings\":[{\"category\":\"NOISE\",\"value\":4}]}"));
  }

  @Given("{word} marked that review helpful")
  public void markedThatReviewHelpful(String actor) throws Exception {
    api.perform(
        api.authorized(post("/api/reviews/" + state.currentReviewId() + "/helpful"), actor));
    assertThat(api.lastStatus()).as("marking the review helpful").isEqualTo(200);
    state.rememberHelpfulVoter(actor);
  }
}

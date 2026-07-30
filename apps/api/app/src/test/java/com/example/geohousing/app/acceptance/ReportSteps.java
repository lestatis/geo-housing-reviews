package com.example.geohousing.app.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

/** Steps about raising a concern and following it. */
public class ReportSteps {

  private final ScenarioState state;
  private final TestApi api;

  public ReportSteps(ScenarioState state, TestApi api) {
    this.state = state;
    this.api = api;
  }

  @When("{word} reports that review for {string} saying {string}")
  public void reportsThatReview(String actor, String category, String description)
      throws Exception {
    submit(actor, category, description);
    if (api.lastStatus() == 201) {
      state.rememberReport(api.readLast("$.reportId"));
    }
  }

  @When("{word} reports that review for {string}")
  public void reportsThatReviewWithoutDetail(String actor, String category) throws Exception {
    reportsThatReview(actor, category, null);
  }

  @Given("{word} reported that review for {string}")
  public void reportedThatReview(String actor, String category) throws Exception {
    submit(actor, category, null);
    assertThat(api.lastStatus()).as("filing a report").isEqualTo(201);
    state.rememberReport(api.readLast("$.reportId"));
  }

  @When("an anonymous visitor reports that review for {string}")
  public void anonymousReports(String category) throws Exception {
    api.perform(api.json(post("/api/reports"), body(category, null)));
  }

  @When("{word} asks for that report")
  public void asksForThatReport(String actor) throws Exception {
    api.perform(api.authorized(get("/api/reports/" + state.currentReportId()), actor));
  }

  @Then("the report is awaiting moderation")
  public void theReportIsAwaitingModeration() {
    assertThat(api.<String>readLast("$.status")).isEqualTo("AWAITING_MODERATION");
  }

  @Then("the report says nothing about the case or any other reporter")
  public void theReportRevealsNothingElse() {
    String body = api.lastBody();
    // A reporter is owed the progress of their own concern and nothing more: the case around it,
    // and anyone else who reported the same content, are not theirs to see.
    assertThat(body).doesNotContain("caseId").doesNotContain("case_id");
    assertThat(body).doesNotContain("reporter").doesNotContain("moderator");
    assertThat(body).doesNotContain("internalNote").doesNotContain("decision");
  }

  private void submit(String actor, String category, String description) throws Exception {
    api.perform(api.json(api.authorized(post("/api/reports"), actor), body(category, description)));
  }

  private String body(String category, String description) {
    return "{\"targetType\":\"REVIEW\",\"targetId\":\""
        + state.currentReviewId()
        + "\",\"category\":\""
        + category
        + "\""
        + (description == null ? "" : ",\"description\":\"" + description + "\"")
        + "}";
  }
}

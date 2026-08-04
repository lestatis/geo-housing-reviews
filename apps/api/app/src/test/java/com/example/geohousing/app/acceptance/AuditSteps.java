package com.example.geohousing.app.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.ArrayList;
import java.util.List;

/** Steps about reading back what administrators and moderators have done. */
public class AuditSteps {

  private final ScenarioState state;
  private final TestApi api;

  public AuditSteps(ScenarioState state, TestApi api) {
    this.state = state;
    this.api = api;
  }

  @When("{word} asks for the audit timeline")
  public void asksForTheTimeline(String actor) throws Exception {
    api.perform(api.authorized(get("/api/admin/audit"), actor));
  }

  @Given("{word} asked for the audit timeline")
  public void askedForTheTimeline(String actor) throws Exception {
    asksForTheTimeline(actor);
    assertThat(api.lastStatus()).as("%s reading the timeline", actor).isEqualTo(200);
  }

  @When("{word} asks for the audit timeline of {word}")
  public void asksForTheTimelineOf(String actor, String subject) throws Exception {
    api.perform(
        api.authorized(get("/api/admin/audit").param("actor", state.accountIdFor(subject)), actor));
  }

  @Then("the timeline records a {string} by {word} in {string}")
  public void theTimelineRecords(String action, String actor, String module) {
    String matching =
        "$.items[?(@.action=='"
            + action
            + "' && @.module=='"
            + module
            + "' && @.actorAccountId=='"
            + state.accountIdFor(actor)
            + "')]";
    assertThat(api.<List<String>>readLast(matching + ".action"))
        .as("a %s by %s in %s", action, actor, module)
        .isNotEmpty();
  }

  @When("{word} asks for the audit timeline of the actor {string}")
  public void asksForTheTimelineOfRawActor(String actor, String raw) throws Exception {
    api.perform(api.authorized(get("/api/admin/audit").param("actor", raw), actor));
  }

  @When("{word} asks for the audit timeline from {string} to {string}")
  public void asksForTheTimelineBetween(String actor, String since, String until) throws Exception {
    api.perform(
        api.authorized(get("/api/admin/audit").param("since", since).param("until", until), actor));
  }

  @Then("the response blames the {string} parameter")
  public void theResponseBlamesTheParameter(String field) {
    // A form can only point at the field that is wrong if the response says which one it is.
    assertThat(api.<List<String>>readLast("$.fieldErrors[*].field")).contains(field);
  }

  /**
   * Reads the start of the timeline page by page, then reads the same window in one page.
   *
   * <p>The window is closed at the moment the walk starts, because reading the timeline records a
   * read: without a fixed end the second request would legitimately see entries the first could
   * not, and the comparison would measure the feedback loop rather than the paging.
   *
   * <p>Only the first few pages, because every scenario in this suite shares one database and the
   * day-long window holds everything they have all done. Three page boundaries is what the
   * assertion needs; walking to the end of a shared history is not.
   */
  @When("{word} reads the whole timeline {int} at a time")
  public void readsTheWholeTimeline(String actor, int pageSize) throws Exception {
    String since = java.time.Instant.now().minus(java.time.Duration.ofDays(1)).toString();
    String until = java.time.Instant.now().toString();

    List<String> walked = new ArrayList<>();
    String cursor = null;
    for (int page = 0; page < 3; page++) {
      var request =
          get("/api/admin/audit")
              .param("since", since)
              .param("until", until)
              .param("limit", String.valueOf(pageSize));
      if (cursor != null) {
        request = request.param("cursor", cursor);
      }
      api.perform(api.authorized(request, actor));
      assertThat(api.lastStatus()).as("page %d of the walk", page).isEqualTo(200);
      walked.addAll(api.<List<String>>readLast("$.items[*].at"));
      cursor = api.<String>readLast("$.nextCursor");
      if (cursor == null) {
        break;
      }
    }
    state.rememberTimelineWalk(walked);

    api.perform(
        api.authorized(
            get("/api/admin/audit")
                .param("since", since)
                .param("until", until)
                .param("limit", "200"),
            actor));
  }

  @Then("the walk reads the same entries as one page of the same window")
  public void theWalkReadsTheSameEntries() {
    List<String> walked = state.timelineWalk();
    assertThat(walked)
        .as("the scenario must cross a page boundary for this to mean anything")
        .hasSizeGreaterThan(2);

    List<String> wholePage = api.<List<String>>readLast("$.items[*].at");
    assertThat(wholePage).hasSizeGreaterThanOrEqualTo(walked.size());
    assertThat(walked)
        .as("a walk that skipped or repeated an entry is a timeline that cannot be trusted")
        .isEqualTo(wholePage.subList(0, walked.size()));
  }

  @Then("the timeline names nobody but {word}")
  public void theTimelineNamesNobodyBut(String actor) {
    // The filter has to reach every module, not be applied after the merge — otherwise "what has
    // this moderator been doing" quietly answers with somebody else's actions too.
    assertThat(api.<List<String>>readLast("$.items[*].actorAccountId"))
        .allMatch(state.accountIdFor(actor)::equals);
  }
}

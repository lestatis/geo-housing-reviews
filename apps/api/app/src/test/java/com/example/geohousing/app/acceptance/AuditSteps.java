package com.example.geohousing.app.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
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

  @Then("the timeline names nobody but {word}")
  public void theTimelineNamesNobodyBut(String actor) {
    // The filter has to reach every module, not be applied after the merge — otherwise "what has
    // this moderator been doing" quietly answers with somebody else's actions too.
    assertThat(api.<List<String>>readLast("$.items[*].actorAccountId"))
        .allMatch(state.accountIdFor(actor)::equals);
  }
}

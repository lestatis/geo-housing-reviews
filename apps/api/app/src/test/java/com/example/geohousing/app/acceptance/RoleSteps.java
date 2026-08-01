package com.example.geohousing.app.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

/**
 * Steps about who may administer the platform.
 *
 * <p>Reaching the moderation queue is the test of whether a role is really in force: the security
 * chain reads it from our own account table on every request, so a grant that did not take effect
 * shows up here rather than in a response body that merely says it did.
 */
public class RoleSteps {

  private final ScenarioState state;
  private final TestApi api;

  public RoleSteps(ScenarioState state, TestApi api) {
    this.state = state;
    this.api = api;
  }

  @When("{word} makes {word} an administrator")
  public void makesAnAdministrator(String actor, String target) throws Exception {
    changeRole(actor, target, "ADMIN", api.accountVersionOf(target));
  }

  @When("{word} makes {word} an administrator using version {long}")
  public void makesAnAdministratorUsingVersion(String actor, String target, long version)
      throws Exception {
    changeRole(actor, target, "ADMIN", version);
  }

  @When("{word} removes {word}'s administrative access")
  public void removesAdministrativeAccess(String actor, String target) throws Exception {
    changeRole(actor, target, "USER", api.accountVersionOf(target));
  }

  @Given("{word} removed {word}'s administrative access")
  public void removedAdministrativeAccess(String actor, String target) throws Exception {
    removesAdministrativeAccess(actor, target);
    assertThat(api.lastStatus()).as("removing %s's access", target).isEqualTo(200);
  }

  @When("{word} removes their own administrative access")
  public void removesTheirOwnAdministrativeAccess(String actor) throws Exception {
    changeRole(actor, actor, "USER", api.accountVersionOf(actor));
  }

  @Given("{word} is the only administrator")
  public void isTheOnlyAdministrator(String actor) {
    api.leaveOnlyAdministrator(state.accountIdFor(actor));
  }

  @Given("{word} removed their own administrative access")
  public void removedTheirOwnAdministrativeAccess(String actor) throws Exception {
    removesTheirOwnAdministrativeAccess(actor);
    assertThat(api.lastStatus()).as("%s stepping down", actor).isEqualTo(200);
  }

  @Then("{word} can reach the moderation queue")
  public void canReachTheModerationQueue(String actor) throws Exception {
    api.perform(api.authorized(get("/api/admin/moderation/cases"), actor));
    assertThat(api.lastStatus()).as("%s reaching the queue", actor).isEqualTo(200);
  }

  @Then("{word} can no longer reach the moderation queue")
  public void canNoLongerReachTheModerationQueue(String actor) throws Exception {
    api.perform(api.authorized(get("/api/admin/moderation/cases"), actor));
    assertThat(api.lastStatus()).as("%s reaching the queue", actor).isEqualTo(403);
  }

  private void changeRole(String actor, String target, String role, long version) throws Exception {
    api.perform(
        api.json(
            api.authorized(
                patch("/api/admin/accounts/" + state.accountIdFor(target) + "/role"), actor),
            "{\"role\":\"" + role + "\",\"version\":" + version + "}"));
  }
}

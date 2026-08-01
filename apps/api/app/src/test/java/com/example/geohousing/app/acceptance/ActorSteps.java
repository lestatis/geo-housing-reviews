package com.example.geohousing.app.acceptance;

import io.cucumber.java.en.Given;

/**
 * Introduces the people a scenario talks about. Each gets a fresh account, so scenarios cannot
 * interfere with one another through a shared identity.
 */
public class ActorSteps {

  private final ScenarioState state;
  private final TestApi api;

  public ActorSteps(ScenarioState state, TestApi api) {
    this.state = state;
    this.api = api;
  }

  @Given("a resident {string}")
  public void aResident(String name) throws Exception {
    state.rememberActor(name, api.signIn(name));
    state.rememberAccountId(name, api.accountIdOf(name));
  }

  @Given("an administrator {string}")
  public void anAdministrator(String name) throws Exception {
    state.rememberActor(name, api.signIn(name));
    state.rememberAccountId(name, api.accountIdOf(name));
    api.grantAdministrator(name);
    state.rememberAdministrator(name);
  }
}

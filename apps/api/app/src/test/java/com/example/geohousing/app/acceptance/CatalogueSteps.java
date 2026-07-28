package com.example.geohousing.app.acceptance;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;

/** Steps about the property catalogue and the public review listing. */
public class CatalogueSteps {

  private final ScenarioState state;
  private final TestApi api;

  public CatalogueSteps(ScenarioState state, TestApi api) {
    this.state = state;
    this.api = api;
  }

  @Given("{word} created the property {string}")
  public void createdTheProperty(String actor, String propertyName) throws Exception {
    state.rememberProperty(propertyName, api.createProperty(actor, propertyName));
  }

  @When("an anonymous visitor asks for the reviews of {string}")
  public void anonymousAsksForReviews(String propertyName) throws Exception {
    api.perform(get("/api/properties/" + state.propertyIdFor(propertyName) + "/reviews"));
  }

  @When("{word} asks for the reviews of {string}")
  public void actorAsksForReviews(String actor, String propertyName) throws Exception {
    api.perform(
        api.authorized(
            get("/api/properties/" + state.propertyIdFor(propertyName) + "/reviews"), actor));
  }
}

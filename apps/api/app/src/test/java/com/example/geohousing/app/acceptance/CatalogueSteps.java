package com.example.geohousing.app.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.List;

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

  @When("an anonymous visitor searches for {string}")
  public void anonymousSearchesFor(String text) throws Exception {
    api.perform(get("/api/properties/search?q={q}", text));
  }

  @When("an anonymous visitor searches near the property {string}")
  public void anonymousSearchesNear(String propertyName) throws Exception {
    api.perform(get("/api/properties/search?lat=41.6412&lng=41.6300&radiusMeters=3000"));
  }

  @Given("{word} withdraws that property")
  public void withdrawsThatProperty(String moderator) throws Exception {
    // Withdrawal carries the version the moderator saw, like every other admin lifecycle action.
    api.perform(
        api.json(
            api.authorized(
                post("/api/admin/properties/" + state.lastCreatedPropertyId() + "/hide"),
                moderator),
            "{\"version\":0}"));
    assertThat(api.lastStatus()).as("withdrawing the property").isEqualTo(200);
  }

  @Then("the results include {string}")
  public void theResultsInclude(String canonicalName) {
    assertThat(matchedNames()).anySatisfy(name -> assertThat(name).contains(canonicalName));
  }

  @Then("the results do not include {string}")
  public void theResultsDoNotInclude(String canonicalName) {
    assertThat(matchedNames()).noneSatisfy(name -> assertThat(name).contains(canonicalName));
  }

  @Then("the results are empty")
  public void theResultsAreEmpty() {
    assertThat(api.<Integer>readLast("$.items.length()")).isZero();
  }

  private List<String> matchedNames() {
    return api.readLast("$.items[*].canonicalName");
  }
}

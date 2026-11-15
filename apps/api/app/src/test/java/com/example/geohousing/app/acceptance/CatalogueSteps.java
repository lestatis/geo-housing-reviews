package com.example.geohousing.app.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.List;
import java.util.stream.StreamSupport;

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

  @Given("{word} added a building named {string} on {string} in {string}")
  public void addedBuilding(String actor, String name, String street, String city)
      throws Exception {
    api.createProperty(actor, "BUILDING", name, city, street, null);
  }

  @Given(
      "{word} added a building named {string} on {string} in {string} with original text {string}")
  public void addedBuildingWithOriginalText(
      String actor, String name, String street, String city, String originalText) throws Exception {
    api.createProperty(actor, "BUILDING", name, city, street, originalText);
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

  @Then("the results show {string} as a {string} at {string}")
  public void theResultsShowAsAt(String canonicalName, String type, String street) {
    assertThat(resultRows())
        .anySatisfy(
            row -> {
              assertThat(row.canonicalName()).isEqualTo(canonicalName);
              assertThat(row.type()).isEqualTo(type);
              assertThat(row.street()).isEqualTo(street);
            });
  }

  @Then("the search response contains no address original text")
  public void theSearchResponseContainsNoAddressOriginalText() {
    assertThat(api.lastBody())
        .doesNotContain("originalText")
        .doesNotContain("apt 12")
        .doesNotContain("\"country\"");
  }

  @Then("the search hit for {string} has no address")
  public void theSearchHitHasNoAddress(String canonicalName) throws Exception {
    JsonNode hit =
        StreamSupport.stream(
                new ObjectMapper().readTree(api.lastBody()).path("items").spliterator(), false)
            .filter(item -> item.path("canonicalName").asText().contains(canonicalName))
            .findFirst()
            .orElseThrow(() -> new AssertionError("no search hit matching " + canonicalName));
    assertThat(hit.has("address")).as("the hit carries an address key").isTrue();
    assertThat(hit.get("address").isNull()).as("the hit's address is JSON null").isTrue();
  }

  private List<String> matchedNames() {
    return api.readLast("$.items[*].canonicalName");
  }

  private List<ResultRow> resultRows() {
    List<String> names = api.readLast("$.items[*].canonicalName");
    List<String> types = api.readLast("$.items[*].type");
    List<java.util.Map<String, Object>> addresses = api.readLast("$.items[*].address");
    return java.util.stream.IntStream.range(0, names.size())
        .mapToObj(
            index ->
                new ResultRow(
                    names.get(index),
                    types.get(index),
                    (String) addresses.get(index).get("street")))
        .toList();
  }

  private record ResultRow(String canonicalName, String type, String street) {}
}

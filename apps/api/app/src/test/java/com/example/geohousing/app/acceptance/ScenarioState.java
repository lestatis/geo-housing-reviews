package com.example.geohousing.app.acceptance;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.test.web.servlet.MvcResult;

/**
 * What one scenario knows: who is acting, what has been created, and what the last request
 * answered.
 *
 * <p>Scenario-scoped. Steps refer to people and places by the names the feature file uses ("Nino",
 * "Vake Heights") and this translates them to tokens and ids, so a scenario never mentions a UUID
 * or a bearer token — those are mechanics, not behaviour.
 */
public class ScenarioState {

  private final Map<String, String> actorTokens = new HashMap<>();
  private final Map<String, String> actorAccountIds = new HashMap<>();
  private final Map<String, String> propertyIds = new HashMap<>();
  private final Set<String> helpfulVoters = new LinkedHashSet<>();

  private String currentReviewId;
  private String currentVerificationCaseId;
  private String currentReportId;
  private String currentAppealId;
  private String lastCreatedPropertyId;
  private MvcResult lastResult;

  public void rememberActor(String name, String bearerToken) {
    actorTokens.put(name, bearerToken);
  }

  public String tokenFor(String actorName) {
    String token = actorTokens.get(actorName);
    if (token == null) {
      throw new IllegalStateException(
          "no actor named '" + actorName + "' in this scenario; introduce them in a Given step");
    }
    return token;
  }

  public void rememberAccountId(String actorName, String accountId) {
    actorAccountIds.put(actorName, accountId);
  }

  public String accountIdFor(String actorName) {
    return actorAccountIds.get(actorName);
  }

  /**
   * Records that this actor marked something helpful, so a scenario can assert that their identity
   * never surfaces in a public representation.
   */
  public void rememberHelpfulVoter(String actorName) {
    helpfulVoters.add(actorName);
  }

  public Set<String> helpfulVoters() {
    return Set.copyOf(helpfulVoters);
  }

  /** Every account this scenario introduced, for asserting that none of them leaks into a view. */
  public java.util.Collection<String> knownAccountIds() {
    return java.util.Set.copyOf(actorAccountIds.values());
  }

  public void rememberProperty(String name, String propertyId) {
    propertyIds.put(name, propertyId);
    this.lastCreatedPropertyId = propertyId;
  }

  /** The property most recently created — "that property" in the feature files. */
  public String lastCreatedPropertyId() {
    if (lastCreatedPropertyId == null) {
      throw new IllegalStateException("this scenario has not created a property yet");
    }
    return lastCreatedPropertyId;
  }

  public String propertyIdFor(String propertyName) {
    String id = propertyIds.get(propertyName);
    if (id == null) {
      throw new IllegalStateException(
          "no property named '" + propertyName + "' in this scenario; create it in a Given step");
    }
    return id;
  }

  public void rememberReview(String reviewId) {
    this.currentReviewId = reviewId;
  }

  /** The review the scenario is talking about — "that review" in the feature files. */
  public String currentReviewId() {
    if (currentReviewId == null) {
      throw new IllegalStateException("this scenario has not created a review yet");
    }
    return currentReviewId;
  }

  public void rememberVerificationCase(String caseId) {
    this.currentVerificationCaseId = caseId;
  }

  public String currentVerificationCaseId() {
    if (currentVerificationCaseId == null) {
      throw new IllegalStateException("this scenario has not opened a verification case yet");
    }
    return currentVerificationCaseId;
  }

  public void rememberReport(String reportId) {
    this.currentReportId = reportId;
  }

  /** The report the scenario is talking about — "that report" in the feature files. */
  public String currentReportId() {
    if (currentReportId == null) {
      throw new IllegalStateException("this scenario has not filed a report yet");
    }
    return currentReportId;
  }

  public void rememberAppeal(String appealId) {
    this.currentAppealId = appealId;
  }

  /** The appeal the scenario is talking about — "that appeal" in the feature files. */
  public String currentAppealId() {
    if (currentAppealId == null) {
      throw new IllegalStateException("this scenario has not filed an appeal yet");
    }
    return currentAppealId;
  }

  public void rememberResult(MvcResult result) {
    this.lastResult = result;
  }

  public MvcResult lastResult() {
    if (lastResult == null) {
      throw new IllegalStateException("this scenario has not made a request yet");
    }
    return lastResult;
  }
}

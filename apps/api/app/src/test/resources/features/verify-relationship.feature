Feature: Verify a relationship to improve the trust signal

  Verification checks that a reviewer had the relationship with the property they claim.
  It never certifies that the review's statements are true (DECISION_LOG P-A03), and an
  unverified review stays visible (P-A01).

  Scenario: approving a verification raises the tier on the reviewer's published review
    Given a resident "Nino"
    And an administrator "Mari"
    And Nino created the property "Vake Heights"
    And Nino published a review of "Vake Heights" saying "მეხუთე წელია აქ ვცხოვრობ"
    And Nino opened a verification case for "Vake Heights"
    When Mari approves the verification with reason "CODE_CONFIRMED"
    Then the request succeeds
    And that review's verification tier is "RELATIONSHIP_SIGNAL"

  Scenario: revoking a verification returns the review to unverified without deleting it
    Given a resident "Dato"
    And an administrator "Mari"
    And Dato created the property "Saburtalo Court"
    And Dato published a review of "Saburtalo Court" saying "მშვიდი უბანია"
    And Dato opened a verification case for "Saburtalo Court"
    And Mari approves the verification with reason "CODE_CONFIRMED"
    When Mari revokes the verification with reason "EVIDENCE_DISPUTED"
    Then the request succeeds
    And that review's verification tier is "UNVERIFIED"

  Scenario: a verification case is private to the account it belongs to
    Given a resident "Ana"
    And a resident "Giorgi"
    And Ana created the property "Mtatsminda View"
    And Ana opened a verification case for "Mtatsminda View"
    When Giorgi asks for that verification case
    Then the content is reported as not found

Feature: Moderate and administer without touching the database

  An operator must be able to run the platform through its own interfaces. Every
  moderation action carries a reason code, and no ordinary account can reach the queue.

  Scenario: a moderator publishes a review that was awaiting moderation
    Given a resident "Nino"
    And an administrator "Mari"
    And Nino created the property "Vake Heights"
    And Nino submitted a review of "Vake Heights" saying "კარგი მდებარეობა"
    When Mari publishes that review with reason "CLEAN"
    Then the request succeeds
    And the review is published

  Scenario: a moderator withholds a published review and gives a reason
    Given a resident "Dato"
    And an administrator "Mari"
    And Dato created the property "Saburtalo Court"
    And Dato published a review of "Saburtalo Court" saying "მეზობლის ნომერია ჩაწერილი"
    When Mari hides that review with reason "PRIVACY_RISK"
    Then the request succeeds
    When an anonymous visitor asks for the reviews of "Saburtalo Court"
    Then the listing contains 0 reviews

  Scenario: a moderation action without a reason code is refused
    Given a resident "Ana"
    And an administrator "Mari"
    And Ana created the property "Gldani Block"
    And Ana submitted a review of "Gldani Block" saying "ტექსტი"
    When Mari publishes that review with reason ""
    Then the request is refused as invalid

  Scenario: an ordinary account cannot reach an administration queue
    Given a resident "Giorgi"
    When Giorgi asks for the verification queue
    Then the request is rejected as forbidden

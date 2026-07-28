Feature: Find a property and understand the experience of living there

  The catalogue and published reviews are readable without an account, so the platform
  is useful before it asks anyone for anything (DECISION_LOG P-011). What a visitor may
  read is limited to content that has cleared moderation.

  Scenario: an anonymous visitor reads a property's published reviews
    Given a resident "Nino"
    And Nino created the property "Vake Heights"
    And Nino published a review of "Vake Heights" saying "მშვიდი ეზო და კარგი მეზობლები"
    When an anonymous visitor asks for the reviews of "Vake Heights"
    Then the request succeeds
    And the listing contains 1 review

  Scenario: a review still awaiting moderation is not public
    Given a resident "Dato"
    And Dato created the property "Saburtalo Court"
    And Dato submitted a review of "Saburtalo Court" saying "ჯერ არ შემოწმებულა"
    When an anonymous visitor asks for the reviews of "Saburtalo Court"
    Then the request succeeds
    And the listing contains 0 reviews

  Scenario: reading is open but writing always requires an account
    Given a resident "Ana"
    And Ana created the property "Mtatsminda View"
    When an anonymous visitor tries to submit a review of "Mtatsminda View"
    Then the request is rejected as unauthenticated

  Scenario: the helpful count is public but the people behind it are never named
    Given a resident "Giorgi"
    And a resident "Salome"
    And Giorgi created the property "Didube Residence"
    And Giorgi published a review of "Didube Residence" saying "ლიფტი ხშირად ჩერდება"
    And Salome marked that review helpful
    When an anonymous visitor asks for that review
    Then the request succeeds
    And the review shows 1 helpful signal
    And no voter identity appears in the response

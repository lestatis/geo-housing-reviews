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

  Scenario: a resident finds a building by part of its name
    Given a resident "Nino"
    And Nino created the property "Orbi Sea Towers Residence"
    When an anonymous visitor searches for "orbi"
    Then the request succeeds
    And the results include "Orbi Sea Towers Residence"

  Scenario: a misspelt name still finds the building
    Given a resident "Dato"
    And Dato created the property "Alliance Palace"
    When an anonymous visitor searches for "alliancce"
    Then the results include "Alliance Palace"

  Scenario: a property somebody added is findable straight away
    Given a resident "Ana"
    And Ana created the property "Vake Garden House"
    When an anonymous visitor searches for "vake garden"
    Then the results include "Vake Garden House"

  Scenario: a property with no address is found, with no address
    Given a resident "Ana"
    And Ana created the property "Unaddressed House"
    When an anonymous visitor searches for "unaddressed"
    Then the results include "Unaddressed House"
    And the search hit for "Unaddressed House" has no address

  Scenario: two buildings with the same name are told apart by their address
    Given a resident "Nino"
    And Nino added a building named "Sunrise Residence" on "Rustaveli Street" in "Batumi"
    And Nino added a building named "Sunrise Residence" on "Chavchavadze Avenue" in "Batumi"
    When an anonymous visitor searches for "sunrise residence"
    Then the results show "Sunrise Residence" as a "BUILDING" at "Rustaveli Street"
    And the results show "Sunrise Residence" as a "BUILDING" at "Chavchavadze Avenue"

  Scenario: the address text a resident typed is never shown in search results
    Given a resident "Dato"
    And Dato added a building named "Sunset Tower" on "Rustaveli Street" in "Batumi" with original text "Sunset Tower, apt 12"
    When an anonymous visitor searches for "sunset"
    Then the results include "Sunset Tower"
    And the search response contains no address original text

  Scenario: the address text a resident typed is never shown on the property's public page
    Given a resident "Dato"
    And Dato added a building named "Sunset Tower" on "Rustaveli Street" in "Batumi" with original text "Sunset Tower, apt 12"
    When an anonymous visitor opens that property
    Then the request succeeds
    And the public response contains no address original text
    When an anonymous visitor lists the catalogue
    Then the request succeeds
    And the public response contains no address original text

  Scenario: a reviewer is never identified to the public
    Given a resident "Nino"
    And Nino created the property "Vake Heights"
    And Nino published a review of "Vake Heights" saying "მშვიდი ეზო და კარგი მეზობლები"
    When an anonymous visitor asks for the reviews of "Vake Heights"
    Then the request succeeds
    And no internal account identifier is exposed
    When an anonymous visitor asks for that review
    Then the request succeeds
    And no internal account identifier is exposed

  Scenario: a moderator still sees who wrote a review
    Given a resident "Nino"
    And an administrator "Mari"
    And Nino created the property "Vake Heights"
    And Nino submitted a review of "Vake Heights" saying "კარგი მდებარეობა"
    When Mari publishes that review with reason "CLEAN"
    Then the request succeeds
    And the response identifies the review's author as "Nino"

  Scenario: an administrator still sees the address text a resident typed
    Given a resident "Dato"
    And an administrator "Mari"
    And Dato added a building named "Sunset Tower" on "Rustaveli Street" in "Batumi" with original text "Sunset Tower, apt 12"
    When Mari activates that property
    Then the request succeeds
    And the response carries the address original text "Sunset Tower, apt 12"

  Scenario: a property an administrator withdrew is not findable
    Given a resident "Giorgi"
    And an administrator "Mari"
    And Giorgi created the property "Withdrawn Tower"
    And Mari withdraws that property
    When an anonymous visitor searches for "withdrawn tower"
    Then the results do not include "Withdrawn Tower"

  Scenario: searching for nothing in particular is refused
    When an anonymous visitor searches for ""
    Then the request is refused as invalid

  Scenario: nothing matches gibberish
    Given a resident "Salome"
    And Salome created the property "Digomi Heights"
    When an anonymous visitor searches for "qqzzxx"
    Then the results are empty

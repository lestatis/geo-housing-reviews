Feature: Report content and have the concern resolved safely

  Anyone signed in can raise a concern about a published review. A report is evidence, never a
  verdict — a moderator still judges the content. What a reporter may learn afterwards is limited
  to their own report: never the case, never who else reported, never a moderator's internal note.

  Scenario: a resident reports a review that exposes a neighbour
    Given a resident "Nino"
    And a resident "Dato"
    And Nino created the property "Vake Heights"
    And Nino published a review of "Vake Heights" saying "მეზობლის ბინის ნომერია 47"
    When Dato reports that review for "PERSONAL_DATA" saying "It names my neighbour's flat."
    Then the request succeeds
    And the report is awaiting moderation

  Scenario: reporting requires an account
    Given a resident "Ana"
    And Ana created the property "Saburtalo Court"
    And Ana published a review of "Saburtalo Court" saying "ხმაურიანი ეზო"
    When an anonymous visitor reports that review for "PERSONAL_DATA"
    Then the request is rejected as unauthenticated

  Scenario: an author cannot report their own review
    Given a resident "Giorgi"
    And Giorgi created the property "Didube Residence"
    And Giorgi published a review of "Didube Residence" saying "ლიფტი არ მუშაობს"
    When Giorgi reports that review for "FALSE_OR_MISLEADING" saying "I changed my mind."
    Then the request is rejected as forbidden
    And the response explains the problem with code "SELF_REPORT_NOT_ALLOWED"

  Scenario: the same account cannot report the same review twice
    Given a resident "Salome"
    And a resident "Tamar"
    And Salome created the property "Gldani Block"
    And Salome published a review of "Gldani Block" saying "სადარბაზო ჭუჭყიანია"
    And Tamar reported that review for "PERSONAL_DATA"
    When Tamar reports that review for "HARASSMENT_OR_THREAT" saying "And it is abusive."
    Then the request is refused as a conflict
    And the response explains the problem with code "REPORT_ALREADY_EXISTS"

  Scenario: reporting a review that is not public does not confirm it exists
    Given a resident "Mari"
    And a resident "Levan"
    And Mari created the property "Isani Towers"
    And Mari submitted a review of "Isani Towers" saying "ჯერ მოდერაციაში"
    When Levan reports that review for "PERSONAL_DATA" saying "Should not be visible."
    Then the content is reported as not found

  Scenario: a reporter can follow their own report
    Given a resident "Nino"
    And a resident "Dato"
    And Nino created the property "Mtatsminda View"
    And Nino published a review of "Mtatsminda View" saying "ცუდი გათბობა"
    And Dato reported that review for "NOT_ABOUT_THIS_PROPERTY"
    When Dato asks for that report
    Then the request succeeds
    And the report is awaiting moderation
    And the report says nothing about the case or any other reporter

  Scenario: one reporter cannot read another reporter's report
    Given a resident "Nino"
    And a resident "Dato"
    And a resident "Ana"
    And Nino created the property "Vera Heights"
    And Nino published a review of "Vera Heights" saying "მშენებლობის ხმაური"
    And Dato reported that review for "PERSONAL_DATA"
    When Ana asks for that report
    Then the content is reported as not found

  Scenario: a report of category other must say what is wrong
    Given a resident "Nino"
    And a resident "Dato"
    And Nino created the property "Avlabari Court"
    And Nino published a review of "Avlabari Court" saying "კარგი მდებარეობა"
    When Dato reports that review for "OTHER" saying ""
    Then the request is refused as invalid

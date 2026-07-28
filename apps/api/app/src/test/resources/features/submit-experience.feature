Feature: Submit an experience and have it published safely

  Every review is pre-moderated before it becomes public (DECISION_LOG P-005), because at
  launch every account is new and untrusted. An edit appends an immutable version rather
  than rewriting what a moderator already judged.

  Scenario: a submitted review waits for moderation rather than appearing at once
    Given a resident "Nino"
    And Nino created the property "Vake Heights"
    When Nino submits a review of "Vake Heights" saying "სადარბაზო სუფთაა"
    Then the request succeeds
    And the review is awaiting moderation

  Scenario: an author sees their own unpublished review but a stranger is told it does not exist
    Given a resident "Dato"
    And a resident "Ana"
    And Dato created the property "Saburtalo Court"
    And Dato submitted a review of "Saburtalo Court" saying "მოდერაციის მოლოდინში"
    When Dato asks for that review
    Then the request succeeds
    When Ana asks for that review
    Then the content is reported as not found

  Scenario: editing a published review appends a version and sends it back for moderation
    Given a resident "Giorgi"
    And Giorgi created the property "Didube Residence"
    And Giorgi published a review of "Didube Residence" saying "პარკინგი ცოტაა"
    When Giorgi edits that review to say "პარკინგი ცოტაა, მაგრამ დაემატა" because "დავაზუსტე"
    Then the request succeeds
    And the review is at version 2
    And the review is awaiting moderation

  Scenario: an edit must say why it was made
    Given a resident "Salome"
    And Salome created the property "Gldani Block"
    And Salome submitted a review of "Gldani Block" saying "ხმაურიანია"
    When Salome edits that review to say "ძალიან ხმაურიანია" because ""
    Then the request is refused as invalid

  Scenario: one live review per author per property
    Given a resident "Tamar"
    And Tamar created the property "Isani Towers"
    And Tamar submitted a review of "Isani Towers" saying "პირველი შთაბეჭდილება"
    When Tamar submits a review of "Isani Towers" saying "მეორე მიმოხილვა"
    Then the request is refused as a conflict
    And the response explains the problem with code "REVIEW_ALREADY_EXISTS"

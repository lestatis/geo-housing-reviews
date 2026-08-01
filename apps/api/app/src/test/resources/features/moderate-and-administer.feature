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

  Scenario: a reported review reaches the moderation queue
    Given a resident "Nino"
    And a resident "Dato"
    And an administrator "Mari"
    And Nino created the property "Vera Court"
    And Nino published a review of "Vera Court" saying "მეზობლის ნომერი 12"
    And Dato reported that review for "PERSONAL_DATA"
    When Mari asks for the moderation queue
    Then the request succeeds
    And the queue holds 1 case awaiting a moderator

  Scenario: the queue counts concerns without naming who raised them
    Given a resident "Nino"
    And a resident "Dato"
    And a resident "Ana"
    And an administrator "Mari"
    And Nino created the property "Chugureti Lofts"
    And Nino published a review of "Chugureti Lofts" saying "სარდაფი დატბორილია"
    And Dato reported that review for "PERSONAL_DATA"
    And Ana reported that review for "HARASSMENT_OR_THREAT"
    When Mari opens that case
    Then the request succeeds
    And the case shows 2 concerns
    And no reporter identity appears in the response

  Scenario: a moderator removes reported content and it disappears for readers
    Given a resident "Nino"
    And a resident "Dato"
    And an administrator "Mari"
    And Nino created the property "Nutsubidze Heights"
    And Nino published a review of "Nutsubidze Heights" saying "მეზობლის ტელეფონია ჩაწერილი"
    And Dato reported that review for "PERSONAL_DATA"
    When Mari decides that case as "REMOVE" for "DOXXING" explaining "Your review named a neighbour."
    Then the request succeeds
    When an anonymous visitor asks for the reviews of "Nutsubidze Heights"
    Then the listing contains 0 reviews

  Scenario: dismissing a concern leaves the review standing
    Given a resident "Nino"
    And a resident "Dato"
    And an administrator "Mari"
    And Nino created the property "Ortachala Park"
    And Nino published a review of "Ortachala Park" saying "ცუდი მომსახურება"
    And Dato reported that review for "FALSE_OR_MISLEADING"
    When Mari decides that case as "APPROVE" for "CLEAN" explaining ""
    Then the request succeeds
    When an anonymous visitor asks for the reviews of "Ortachala Park"
    Then the listing contains 1 review

  Scenario: an adverse decision must explain itself to the author
    Given a resident "Nino"
    And a resident "Dato"
    And an administrator "Mari"
    And Nino created the property "Digomi Gardens"
    And Nino published a review of "Digomi Gardens" saying "ლიფტი გაფუჭებულია"
    And Dato reported that review for "PERSONAL_DATA"
    When Mari decides that case as "REMOVE" for "DOXXING" explaining ""
    Then the request is refused as invalid

  Scenario: an ordinary account cannot reach the moderation queue
    Given a resident "Levan"
    When Levan asks for the moderation queue
    Then the request is rejected as forbidden

  Scenario: an administrator grants administrative access to a resident
    Given an administrator "Mari"
    And a resident "Nino"
    When Mari makes Nino an administrator
    Then the request succeeds
    And Nino can reach the moderation queue

  Scenario: an administrator removes another administrator's access
    Given an administrator "Mari"
    And an administrator "Tekla"
    When Mari removes Tekla's administrative access
    Then the request succeeds
    And Tekla can no longer reach the moderation queue

  Scenario: an administrator steps down while another remains
    Given an administrator "Mari"
    And an administrator "Tekla"
    When Tekla removes their own administrative access
    Then the request succeeds
    And Tekla can no longer reach the moderation queue
    And Mari can reach the moderation queue

  Scenario: the last administrator cannot step down
    Given an administrator "Mari"
    And Mari is the only administrator
    When Mari removes their own administrative access
    Then the request is refused as a conflict
    And the response explains the problem with code "LAST_ADMINISTRATOR"
    And Mari can reach the moderation queue

  Scenario: a role change carrying a stale version is refused
    Given an administrator "Mari"
    And a resident "Nino"
    When Mari makes Nino an administrator using version 7
    Then the request is refused as a conflict

  Scenario: an ordinary account cannot change anyone's role
    Given a resident "Nino"
    And a resident "Dato"
    When Nino makes Dato an administrator
    Then the request is rejected as forbidden

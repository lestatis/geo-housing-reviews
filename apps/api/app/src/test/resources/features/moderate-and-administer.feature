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

  Scenario: a moderator finds the account behind a pseudonym
    Given an administrator "Mari"
    And a resident "Nino"
    When Mari looks up the account behind Nino's pseudonym
    Then the request succeeds
    And the account found is Nino's

  Scenario: looking up a pseudonym nobody uses finds nothing
    Given an administrator "Mari"
    When Mari looks up the account behind the pseudonym "Nobody-At-All"
    Then the content is reported as not found

  Scenario: a moderator restricts an account and lifts the restriction
    Given an administrator "Mari"
    And a resident "Nino"
    When Mari restricts Nino saying "posted a neighbour's flat number"
    Then the request succeeds
    And Nino is restricted
    When Mari lifts that restriction
    Then the request succeeds
    And Nino is not restricted

  Scenario: an account is not restricted twice over
    Given an administrator "Mari"
    And a resident "Nino"
    And Mari restricted Nino saying "first"
    When Mari restricts Nino saying "second"
    Then the request is refused as a conflict
    And the response explains the problem with code "ALREADY_RESTRICTED"

  Scenario: a restriction must say why
    Given an administrator "Mari"
    And a resident "Nino"
    When Mari restricts Nino saying ""
    Then the request is refused as invalid

  Scenario: an ordinary account cannot restrict anyone
    Given a resident "Nino"
    And a resident "Dato"
    When Nino restricts Dato saying "I disagree with them"
    Then the request is rejected as forbidden

  Scenario: a restricted account cannot submit a review
    Given an administrator "Mari"
    And a resident "Nino"
    And Nino created the property "Digomi Court"
    And Mari restricted Nino saying "posted a neighbour's flat number"
    When Nino submits a review of "Digomi Court" saying "კიდევ ერთი"
    Then the request is rejected as forbidden
    And the response explains the problem with code "ACCOUNT_RESTRICTED"

  Scenario: a restricted account cannot report anyone
    Given an administrator "Mari"
    And a resident "Nino"
    And a resident "Dato"
    And Dato created the property "Nutsubidze Heights"
    And Dato published a review of "Nutsubidze Heights" saying "ხმაურიანი"
    And Mari restricted Nino saying "brigading the report queue"
    When Nino reports that review for "PERSONAL_DATA" saying "I object."
    Then the request is rejected as forbidden

  Scenario: a restricted account can still appeal a decision against it
    Given a resident "Nino"
    And a resident "Dato"
    And an administrator "Mari"
    And an administrator "Tekla"
    And Nino created the property "Ortachala Rise"
    And Nino published a review of "Ortachala Rise" saying "მეზობლის ნომერი"
    And Dato reported that review for "PERSONAL_DATA"
    And Mari decided that case as "REMOVE" for "DOXXING" explaining "Named a neighbour."
    And Tekla restricted Nino saying "repeated personal data"
    When Nino appeals saying "The flat number was my own."
    Then the request succeeds

  Scenario: lifting a restriction lets the account contribute again
    Given an administrator "Mari"
    And a resident "Nino"
    And Nino created the property "Vazisubani Court"
    And Mari restricted Nino saying "spam"
    And Mari lifts that restriction
    When Nino submits a review of "Vazisubani Court" saying "კარგი ადგილი"
    Then the request succeeds

  Scenario: a moderator restricting an account through a decision actually restricts it
    Given a resident "Nino"
    And a resident "Dato"
    And an administrator "Mari"
    And Nino created the property "Lisi Terraces"
    And Nino published a review of "Lisi Terraces" saying "მეზობლის ტელეფონი"
    And Dato reported that review for "HARASSMENT_OR_THREAT"
    When Mari decided that case as "RESTRICT_ACCOUNT" for "HARASSMENT" explaining "Repeated abuse."
    Then Nino is restricted

  Scenario: the audit timeline shows what was done across modules
    Given an administrator "Mari"
    And a resident "Nino"
    And Nino created the property "Chugureti Court"
    And Mari makes Nino an administrator
    And Mari withdraws that property
    When Mari asks for the audit timeline
    Then the request succeeds
    And the timeline records a "GRANT_ADMIN" by Mari in "identity"
    And the timeline records a "HIDE" by Mari in "properties"

  Scenario: the timeline can follow one administrator across modules
    Given an administrator "Mari"
    And an administrator "Tekla"
    And a resident "Nino"
    And Nino created the property "Avlabari Heights"
    And Tekla withdraws that property
    When Mari asks for the audit timeline of Tekla
    Then the request succeeds
    And the timeline records a "HIDE" by Tekla in "properties"
    And the timeline names nobody but Tekla

  Scenario: reading the timeline is itself recorded
    Given an administrator "Mari"
    And Mari asked for the audit timeline
    When Mari asks for the audit timeline
    Then the timeline records a "VIEW_AUDIT" by Mari in "identity"

  Scenario: a timeline longer than one page can be read to the end
    Given an administrator "Mari"
    And a resident "Nino"
    And Nino created the property "Didube Gardens"
    And Mari makes Nino an administrator
    And Mari withdraws that property
    When Mari reads the whole timeline 2 at a time
    Then the walk reads the same entries as one page of the same window

  Scenario: a mistyped actor filter is refused rather than answered for everyone
    Given an administrator "Mari"
    When Mari asks for the audit timeline of the actor "not-an-account-id"
    Then the request is refused as invalid
    And the response explains the problem with code "INVALID_AUDIT_QUERY"
    And the response blames the "actor" parameter

  Scenario: a window that ends before it starts is refused rather than answered with nothing
    Given an administrator "Mari"
    When Mari asks for the audit timeline from "2026-08-04T00:00:00Z" to "2026-08-01T00:00:00Z"
    Then the request is refused as invalid
    And the response explains the problem with code "INVALID_AUDIT_QUERY"
    And the response blames the "until" parameter

  Scenario: an ordinary account cannot read the audit timeline
    Given a resident "Nino"
    When Nino asks for the audit timeline
    Then the request is rejected as forbidden

  Scenario: the metrics screen counts an overturned appeal as one
    # docs/MODERATION.md asks for exactly one measurement by name — "measure overturned decisions".
    Given a resident "Nino"
    And a resident "Dato"
    And an administrator "Mari"
    And an administrator "Tekla"
    And Nino created the property "Saburtalo Heights"
    And Nino published a review of "Saburtalo Heights" saying "ლიფტი არ მუშაობს"
    And Dato reported that review for "PERSONAL_DATA"
    And Mari decided that case as "REMOVE" for "DOXXING" explaining "Removed."
    And Nino appealed saying "Nothing in it identifies anyone."
    And Tekla overturns that appeal explaining "The review named nobody."
    When Mari asks for the metrics
    Then the request succeeds
    And at least 1 appeal was overturned
    And every appeal counted as overturned was also counted as heard

  Scenario: a pending appeal has not been heard
    # Counting it would flatter the queue: an appeal nobody has decided is work outstanding.
    Given a resident "Nino"
    And a resident "Dato"
    And an administrator "Mari"
    And Nino created the property "Gldani Court"
    And Nino published a review of "Gldani Court" saying "მეზობლების ხმაური"
    And Dato reported that review for "PERSONAL_DATA"
    And Mari decided that case as "REMOVE" for "DOXXING" explaining "Removed."
    And Mari asked for the metrics
    When Nino appeals saying "I want it back."
    And Mari asks for the metrics
    Then the count of appeals heard has not changed

  Scenario: the metrics name nobody
    # The audit timeline answers "who did what", with a purpose and a named risk. This answers
    # "is the work being done", and needs no names to do it.
    Given an administrator "Mari"
    And a resident "Nino"
    And Nino created the property "Isani Gardens"
    When Mari asks for the metrics
    Then the request succeeds
    And no account is named in the response

  Scenario: a window that ends before it starts is refused
    Given an administrator "Mari"
    When Mari asks for the metrics from "2026-08-04T00:00:00Z" to "2026-08-01T00:00:00Z"
    Then the request is refused as invalid
    And the response explains the problem with code "INVALID_METRICS_WINDOW"

  Scenario: an ordinary account cannot read the metrics
    Given a resident "Nino"
    When Nino asks for the metrics
    Then the request is rejected as forbidden

  Scenario: a restriction is lifted only through the account that has it
    # A stale or mistyped link puts somebody else's identifier in the path. Lifting the wrong
    # account's restriction is silent: the moderator sees success and the wrong person walks free.
    Given an administrator "Mari"
    And a resident "Nino"
    And a resident "Dato"
    And Mari restricted Nino saying "posted a neighbour's flat number"
    When Mari lifts that restriction through Dato's account
    Then the content is reported as not found
    And Nino is restricted

  Scenario: winning an appeal against a restriction lets the account contribute again
    # The whole point of the reversal. Recording that the decision was wrong while the account
    # stays barred is half an outcome: the author wins and still cannot post.
    Given a resident "Nino"
    And a resident "Dato"
    And an administrator "Mari"
    And an administrator "Tekla"
    And Nino created the property "Mtatsminda Terraces"
    And Nino published a review of "Mtatsminda Terraces" saying "მეზობლის ტელეფონი"
    And Dato reported that review for "HARASSMENT_OR_THREAT"
    And Mari decided that case as "RESTRICT_ACCOUNT" for "HARASSMENT" explaining "Repeated abuse."
    And Nino is restricted
    And Nino appealed saying "I never contacted anyone off the platform."
    When Tekla overturns that appeal explaining "The messages were not from this account."
    Then the request succeeds
    And Nino is not restricted

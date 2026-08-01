# Task handoff

## Objective

Implement plan 012, chunk 2: working a case. Case detail, decide with reason code and explanation,
and an appeals queue a second moderator can actually decide from.

## Active branch

`feat/012-admin-web-chunk2-case-work`, branched from clean `main` at `6a0afd9`. Local only; not
pushed. Awaiting a fresh independent review before merge.

## Related issue or plan

No issue. `docs/plans/012-admin-web-app.md`, chunk 2 of 3.

## Current status

completed, awaiting independent review

## Completed work

**Backend — the appeals queue could not show what was being appealed.**

- `AppealService.pending()` returns `PendingAppeal` (the appeal, the contested `ModerationDecision`,
  and the `ModerationTargetRef`) instead of a bare `Appeal`.
- New `AdminAppealQueueEntryResponse` with a nested `contestedDecision` (case id, target type and
  id, action, reason code, public explanation, internal note, decided-at).
  `AdminAppealQueueResponse.items` now carries it. `AdminAppealResponse` is unchanged and still the
  decide-result shape.
- Two unit tests written first (failed to compile), then the implementation; then a Gherkin scenario
  "the moderator who hears an appeal can read the decision it challenges" with two new steps.
- `docs/api/openapi.json` regenerated; the TS client regenerates from it.

**Frontend — `/moderation/[caseId]` and `/moderation/appeals`.**

- Case detail: summary, concerns, decision history, and a decide form (action, reason code, what the
  author is told, internal note).
- Appeals: each pending appeal shows the contested decision, what the author was told, who decided
  originally, the appellant's text, a link to the case, and uphold/overturn with an explanation.
- All writes are Server Actions (`src/moderation/actions.ts`). Nothing calls the API from the
  browser.
- `src/format.ts` extracted once three views needed the same UTC formatting.

## Remaining work

Plan 012 chunk 3: verification decisions, property activate/hide/merge, account role management.

## Decisions made

- **The appeals API was extended rather than the screen compromised** (founder decision this
  session). A moderator hearing an appeal is by rule not the one who decided it, so a queue showing
  only the appellant's text asks them to decide blind — the due-process failure MODERATION.md
  exists to prevent.
- **The UI mirrors two due-process rules it does not own.** "A takedown must tell the author why"
  and "an appeal outcome must be explained" are invariants on the server
  (`ModerationDecision.requireExplanationWhenAdverse`, `Appeal.checkInvariants`). The form checks
  them so a moderator is told before submitting, not after.
- **`publicExplanation` and `internalNote` are never concatenated**, in the view model or on the
  page. Merging them is how a moderators-only note would eventually follow the author-facing one out.
- **The moderation enums stay undocumented in OpenAPI for now**, so `src/moderation/decision.ts`
  restates `DecisionAction`. Fixing it is a backend decision with real trade-offs — see the plan's
  findings list.

## Changed files

New: `apps/api/.../application/PendingAppeal.java`,
`apps/api/.../infrastructure/web/AdminAppealQueueEntryResponse.java`,
`apps/web/src/format.ts`, `apps/web/src/moderation/{case,appeal,decision,actions}.ts` and their
tests, `apps/web/app/moderation/[caseId]/{page.tsx,decide-form.tsx}`,
`apps/web/app/moderation/appeals/{page.tsx,hear-appeal-form.tsx}`,
`apps/web/e2e/{moderation-case.spec.ts,sign-in.ts}`.

Modified: `AppealService.java`, `AdminAppealController.java`, `AdminAppealQueueResponse.java`,
`AppealServiceTest.java`, `report-and-dispute.feature`, `AppealSteps.java`, `docs/api/openapi.json`,
`apps/web/src/moderation/queue.ts`, `apps/web/app/moderation/page.tsx`,
`apps/web/e2e/{seed.ts,moderation-queue.spec.ts}`, `apps/web/README.md`,
`docs/plans/012-admin-web-app.md`.

## Commands and tests

```bash
./scripts/check.sh                                    # full gate

docker compose -f infra/docker/docker-compose.yml up -d postgres oidc
cd apps/api && OIDC_JWK_SET_URI=http://localhost:8081/default/jwks \
  IDENTITY_AUTH_SUBJECT_PEPPER=local-dev-only-pepper ./gradlew :app:bootRun
cd apps/web && pnpm e2e                               # 11 passed
```

22 Vitest cases. Moderation's mutation threshold raised 85 → 86 (score 195/228). Three non-vacuity
proofs run this chunk:

- changing the expected action in the new Gherkin scenario failed exactly that scenario;
- merging `internalNote` into `publicExplanation` failed 3 Vitest cases and 1 Playwright journey;
- removing both explanation guards failed exactly the two journeys that assert them.

## Failures and blockers

None outstanding. The first full-gate run failed on `spotlessJavaCheck` formatting only; fixed with
`spotlessApply`. Reading the surviving mutants also turned up a stale `build/pitest/mutations.xml`
from 2026-07-28 sitting next to the real `build/reports/pitest/mutations.xml` — anyone inspecting
mutation results by hand should check the path and the timestamp.

## Unresolved risks

- **No way to bootstrap the first administrator.** The e2e seed now grants two admin roles by SQL.
  Still fine locally; still needs an answer before a production environment exists.
- **The moderation enums are undocumented in OpenAPI**, so the client restates `DecisionAction`.
- **The OpenAPI document declares only success responses**, so the generated client types `error` as
  `never`; every page uses `result.response.ok` rather than destructuring.
- **Collision-suffixed `operationId`s** (`queue_1`, `decide_1`, `get_7`).
- One mutant this chunk added is uncovered: the `orElseThrow` for a case missing under a pending
  appeal. Foreign keys make it unreachable and forcing a test double into that state would test the
  double, not the rule.
- Case assignment is implicit: `POST /decide` claims the case if nobody holds it. There is no
  exclusive-assignment UI, so two moderators can still open the same case — noted in plan 009's
  follow-ups, not addressed here.

## Next action

Independent review of this branch by a fresh session that did not implement it, then merge. After
that, plan 012 chunk 3: the verification, property and account queues.

# Plan 017 — the debt found by reviewing what was merged unreviewed

## Context

Ten branches reached `main` without an independent review. On 2026-08-04 the three highest-risk
modules were reviewed as they stand: identity (authorization), verification (the most sensitive
data), moderation (due process).

**Fifteen findings, every one evidenced against a file and line.** Two were contained enough to fix
immediately and are already on `main`-bound branches:

- `75b8052` — the restriction lift endpoint ignored its `{accountId}`, so a lift addressed to one
  account landed on another.
- `e3a8d40` — `VerificationExpiryService` was called by nothing but tests, so no badge ever expired
  in production.

The remaining thirteen are recorded here rather than patched at the end of a session. They are not
independent bugs: most fall into three groups with one cause each, and fixing them piecemeal would
mean touching the same seams three times.

**This plan is an inventory and a proposed order, not an approved scope.** Each group needs its own
plan before implementation.

## Group A — no transaction spans an application use case

`.claude/rules/backend-java.md` already says "define transaction boundaries at application use
cases". Every boundary today sits on a repository method instead — the shape that rule warns
against. Consequences found:

| Severity | Defect |
| --- | --- |
| P1 | Two concurrent demotions can leave **zero administrators**. The last-admin count and the save are separate transactions; recovery would need direct database access. |
| P1 | Stale moderator decisions overwrite case state and append a second decision — the repository re-fetches and applies a detached aggregate without comparing versions. |
| P1 | Stale appeal outcomes overwrite each other, so a restored review can end up recorded as upheld. |
| P2 | Role and restriction mutations commit **without their audit row**. `AccountRoleService` carries a comment claiming "the surrounding transaction rolls the role change back" — there is no surrounding transaction. That comment is worse than none. |
| P2 | The one-active-restriction rule is check-then-insert with no database constraint. |

**Shape of the fix:** a transactional facade per use case in `infrastructure` (the application layer
is deliberately framework-free, so `@Transactional` cannot go on the services), plus optimistic
version checks that actually compare the caller's version, plus database constraints where the
invariant is expressible there. The appeal-count fix in `63a6f79` is the pattern worth repeating:
prefer making an invariant structural over checking it afterwards.

**Do this group first.** It contains the only defect that can lock every administrator out of the
platform.

## Group B — evidence lifecycle

| Severity | Defect |
| --- | --- |
| P1 | The object is written to S3 before its metadata row. If that row fails, raw documents exist that retention can never see, and no reconciliation exists. |
| P1 | A failed terminal deletion leaves evidence until the 30-day upload deadline rather than the configured 7-day post-decision window. That shorter deadline is computed and never used. |
| P2 | Document reuse is computed and never used: SHA-256 is stored, nothing looks it up, so one document can support unrelated accounts without producing the reuse signal the trust model assumes. |

**Not counted here:** the reviewer raised the MIME-header allowlist as P1 — arbitrary bytes are
stored and later served to moderators. ADR-0008 lists scanning and re-encoding as **pre-launch**
requirements and we are pre-launch, so this is known scope, not a defect. It belongs in the launch
checklist, and the gap between "declared type" and "actual bytes" should be named there explicitly.

## Group C — due process and what the author is owed

| Severity | Defect |
| --- | --- |
| P1 | **An author is never told why.** The public explanation is persisted and served only through `/api/admin/**`. The author sees an appeal outcome but never the original reason or policy version — so they appeal a decision whose grounds they have not been shown. |
| P1 | Overturning a `RESTRICT_ACCOUNT` decision does not lift the restriction; the account stays restricted after winning its appeal. |
| P2 | A reporter can decide their own report. Severity is a judgement call: `MODERATION.md` line 102 asks only that "moderators disclose conflicts and can recuse", a human process rather than an enforced constraint — but `P-013` names it among "the rules that actually matter", and the sibling rule (an appeal heard by a different moderator) *is* enforced by a database CHECK. The asymmetry is the finding. |
| P2 | Reports are stranded after a decision or escalation: intake attaches new reports to any non-closed case, but only `IN_REVIEW` cases can be decided. |

The first of these is the one to weigh hardest. `MODERATION.md` requires an explanation specific
enough to correct the issue; the platform writes one and never delivers it.

## Proposed order

1. **Group A**, because of the zero-administrators defect.
2. **Group C's first two**, because they are the ones a user experiences as unfairness.
3. **Group B**, before any real evidence exists to orphan.
4. The rest, sized against whatever else is competing.

## Verification for any of it

```bash
cd apps/api && ./gradlew :app:test --tests '*<Area>*' -PskipMutation
./scripts/check.sh          # read the EXIT= marker
cd apps/web && pnpm e2e     # needs the stack; see apps/web/README.md
```

Concurrency defects need tests that actually run two callers, not two sequential calls. A
version-conflict test that never races proves only that the happy path still works.

## Risks

- **The reviews were per module.** Cross-module paths — a moderation decision restricting an account
  through identity, verification projecting onto reviews — were reviewed only from one side.
- **Three modules were reviewed; there are more.** Properties, reviews and the app layer have not
  had this treatment.
- **Every finding is a review claim until re-verified.** Each was checked against the code once, by
  the agent that also wrote this file. The severity corrections above (MIME, reporter conflict) are
  examples of why a second reading matters.

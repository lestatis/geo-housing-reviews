# Task handoff

## Objective

Work through the debt found by reviewing the modules that reached `main` unreviewed
(`docs/plans/017-unreviewed-module-debt.md`).

## Active branch

`docs/handoff-after-plan-018`, branched from `main` at `030cc18`. Everything below is already on
`main` and `origin/main`.

## Current status

Plan 018 (`docs/plans/018-*`, written into `.claude/plans/`) is **complete and merged** — all three
chunks, each independently reviewed and each with its review findings fixed.

## Completed work

### Reviews

identity, verification and moderation, read-only (`codex exec -s read-only`). **Fifteen findings,
all evidenced.** Two of the reviewer's severities were corrected after checking the documents: the
MIME-header finding is pre-launch scope under ADR-0008, and the reporter-conflict finding is P2
because `MODERATION.md` asks for disclosure and recusal rather than enforcement.

### Fixed and merged

| Commit | What |
| --- | --- |
| `75b8052` | A restriction lift landed on whichever account owned the id, not the one named in the URL |
| `e3a8d40` | Verification badges never expired: the service existed and nothing called it |
| `f2cff48` | The last-administrator lockout — two concurrent demotions could leave zero admins |
| `60dc15b`, `2f9d085`, `1809c49` | The restriction race, and the moderation path that bypassed the fix |
| `a5a6f33`, `030cc18` | Moderation's stale case and appeal writes, and the transaction that undoes them |

### The pattern worth carrying forward

Every chunk's review found the same *shape* of mistake: a guard added where the defect was noticed,
with a sibling path or a second half left untouched.

- Chunk 1: the transaction rolled back the audit rows for refused attempts.
- Chunk 2: the same refusal-audit defect again, one commit later, plus `AccountRestraintAdapter`
  still on the concrete class so moderation's path skipped the lock entirely.
- Chunk 3: the facade my own plan specified and I did not write, so the version check fired after
  side effects had already committed.

**The question that would have caught all three: when adding a guard, what else reaches this rule,
and what has already committed by the time the guard runs?**

Three comments in this codebase have now claimed protections the code did not have — the audit
transaction in `AccountRoleService`, the cursor javadoc in the audit timeline, and `@Version` in
`JpaModerationCaseRepository`. A comment asserting safety is worth checking against the code.

## Remaining work

Nine findings in `docs/plans/017-unreviewed-module-debt.md`, none started:

- **Group C, and the two to do first.** An author is never told *why* their content was removed —
  the explanation is written and served only through `/api/admin/**`, so they appeal a decision
  whose grounds they have never seen. And overturning a `RESTRICT_ACCOUNT` decision does not lift
  the restriction, so an account can win its appeal and stay barred.
- **Group B**, evidence lifecycle: orphaned objects, a failed deletion holding evidence 30 days
  instead of 7, an unused reuse signal.
- Plus: the exclusion constraint deferred from chunk 2 (needs `btree_gist` in the root migration
  range), and the restriction race test's residual weakness — six callers make a benign
  interleaving unlikely, not impossible.

Not reviewed at all: properties, reviews, the app layer, and every cross-module path.

## Commands and tests

```bash
./scripts/check.sh          # read the EXIT= marker, never a wrapper's status
cd apps/web && pnpm e2e     # needs the stack; see apps/web/README.md
codex review --base main    # or --commit <sha> once the work is already merged
```

Every fix above was proved by breaking it and watching exactly the intended test fail.

## Failures and blockers

None open. Testcontainers leaks heavily across a long session — 354 instances holding 21.5 GB were
cleared once today. `docker container prune -f` then `docker volume prune -f`, in that order.

## Unresolved risks

- **Reviews landed after merges, repeatedly.** `main` moved under this work three times, so the
  review became a post-merge check rather than a gate. It kept finding real defects, which is the
  argument for deciding deliberately whether merges should wait for it.
- **Every finding is a claim until re-verified**; each was read once by the agent that recorded it.
- Wedged Docker containers still need a privileged `systemctl restart docker`.

## Next action

Group C's first two findings, which are the ones a user experiences as unfairness. Each needs a plan
first: the author-facing explanation adds a public endpoint, and the restriction reversal crosses
moderation into identity.

## Last updated

2026-08-04

# Task handoff

## Objective

Review the modules that reached `main` without an independent review, and fix what is proportionate.

## Active branch

`fix/identity-review-findings`, branched from `main`. Carries both fixes below plus the debt
inventory. (The branch name understates it — it now holds a verification fix too.)

## Current status

in_progress — three modules reviewed, two defects fixed and verified, thirteen findings recorded in
`docs/plans/017-unreviewed-module-debt.md`. `./scripts/check.sh` reports `EXIT=0`.

## Completed work

### Reviewed

identity, verification and moderation, each read-only (`codex exec -s read-only`) against
`AGENTS.md`, `.claude/rules/security.md` and the relevant domain documents. **Fifteen findings, all
evidenced.** Every one was checked against the code before being acted on or recorded; two severity
claims were corrected in the process (see the plan).

### Fixed — identity: a lift landed on the wrong account

`POST /accounts/A/restrictions/{B's restriction}/lift` lifted **B's** restriction while the URL named
A. No privilege escalation — the caller is already an administrator, and the audit row recorded the
true owner — but a moderation action landing on somebody other than the person it names is silent.

Covered at both levels, which mattered: the defect was the *controller* not passing the identifier,
so a service test alone would not have caught it.

### Fixed — verification: badges never expired in production

`VerificationExpiryService.expireLapsed` was written, unit-tested, and called by nothing but tests.
Evidence retention had a scheduled job; expiry had none. A lapsed badge stayed `APPROVED`, kept
projecting its tier onto reviews, and kept feeding ranking.

The test asserts the **trigger**, not the bean: removing `@Scheduled` leaves a perfectly healthy bean
and fails the test. A service that works and is never called is indistinguishable from one that does
not work. Evidence retention — the only thing that deletes raw identity documents — is now covered
the same way.

## Remaining work

`docs/plans/017-unreviewed-module-debt.md` holds thirteen findings in three groups, with a proposed
order. Group A first: it contains the only defect that can lock every administrator out of the
platform.

Not yet reviewed: properties, reviews, the app layer, and every cross-module path.

## Decisions made

- **Stopped fixing after two.** Fifteen findings across three modules is a body of work needing
  prioritisation, not end-of-session patching. The two fixed were contained, provable, and did not
  touch a seam another finding also touches.
- **Two severities corrected against the reviewer.** The MIME-header finding is pre-launch scope per
  ADR-0008, not a defect. The reporter-conflict finding is P2, not P1: `MODERATION.md` asks for
  disclosure and recusal, a human process — though its sibling rule is enforced by a database CHECK,
  and that asymmetry is the real finding.

## Commands and tests

```bash
cd apps/api && ./gradlew :modules:identity:test -PskipMutation
cd apps/api && ./gradlew :app:test --tests '*ScheduledSweepWiringTest' --tests '*Acceptance*' -PskipMutation
./scripts/check.sh          # read the EXIT= marker
```

Both fixes proved non-vacuous by breaking them: neutralising the ownership check fails the unit test
and the acceptance scenario; removing `@Scheduled` fails the wiring test while leaving the bean.

## Failures and blockers

None open.

## Unresolved risks

- **Thirteen recorded findings, five of them P1.** The most serious can leave the platform with zero
  administrators.
- **Reviews were per module**, so cross-module paths were seen from one side only.
- **Every finding is a claim until re-verified.** Each was read once, by the agent that also wrote
  the inventory.
- Unchanged: wedged Docker containers needing a privileged `systemctl restart docker`.

## Next action

A human decision on order. Group A is the recommendation. Each group needs its own plan before
implementation.

## Last updated

2026-08-04

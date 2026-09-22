# Plan 022 — make review a gate, not a post-mortem

Status: In progress
Owner: Claude Code
Related issue: None
Last updated: 2026-09-22

## Objective

Protect `main` so that nothing reaches it without a pull request whose required checks passed on
the exact commit being merged — and so that the independent review the repository's own process
describes happens *before* the merge rather than after it.

## Why now

Plan 021 closed with this as its named non-goal: "an administrator action after CI is green." CI is
green. And the history that makes it urgent is recorded in `docs/handoffs/`: during plans 015–019,
`main` moved under in-flight work three times, each time by a direct push, and each time the
independent review became a post-merge check. It kept finding real defects — a lockout, a deadlock,
audit rows silently rolled back — every one of which shipped to `main` first and was fixed after.
`CONTRIBUTING.md` says "do not develop directly on `main`" and describes a five-step review loop
ending in "a human decides whether to merge". Nothing enforces either sentence.

## Acceptance criteria

- [ ] A direct `git push origin main` is refused for everyone, administrators included.
- [ ] A pull request cannot merge until `Governance`, `Backend` and `Frontend` have all reported
      success on the head commit, with the branch up to date with `main`.
- [ ] A docs-only pull request still merges: every required check reports a result rather than
      waiting forever on a workflow that never ran.
- [ ] Force-pushes to and deletion of `main` are refused.
- [ ] The rule is checked into the repository as a ruleset file, so it can be reviewed, diffed and
      re-applied rather than reconstructed from memory in the settings UI.

## Non-goals

- Requiring a human approval from a second person. There is one maintainer; requiring one approval
  would block them from merging anything, since GitHub does not count self-approval. See Decisions.
- Running `codex review` as a CI check. It needs an API secret and costs money per pull request. It
  is the mechanical form of the review loop and belongs in a follow-up once this rule has held for a
  while — recorded below, not started here.
- Protecting any branch other than `main`.

## Current system

Three path-filtered workflows (`backend-check.yml`, `frontend-check.yml`, `governance-check.yml`),
each with one job named after the workflow's scope and no explicit `name:`. `.github/CODEOWNERS`
exists with placeholder handles and a path (`apps/admin/`) that does not exist; `apps/web/` is the
admin app. No branch protection or ruleset of any kind.

**Two traps in that starting point, both of which would make a naïve rule worse than none:**

1. **Path filters and required checks do not mix.** A required check that is skipped — because the
   pull request touched nothing under its `paths:` — never reports. GitHub shows "Expected — waiting
   for status to be reported" and the pull request is blocked *forever*. Every docs-only change would
   be unmergeable. The jobs have to run on every pull request and decide for themselves whether
   there is anything to do.

2. **A rule administrators can bypass is decorative.** GitHub's default lets repository admins push
   through protection. The entire history this plan responds to is an administrator pushing to
   `main`. The bypass list must be empty.

## Decisions

- **Pull request required, zero approvals required.** The rule enforces *process* — a pull request,
  green checks, conversation resolution — not a second pair of human eyes, because there is not one.
  The independent review stays a process rule (`CONTRIBUTING.md` §Review loop) until the follow-up
  makes it a check. Raising `required_approving_review_count` to 1 is a one-line change to the
  ruleset when a second maintainer exists.
- **Checks always run; work is conditional inside them.** Each job diffs the pull request against its
  base and skips its expensive steps when nothing relevant changed, reporting success in seconds.
  The `push: main` triggers keep their path filters — post-merge runs do not affect protection.
  Inline `git diff`, not a marketplace action: no new dependency for a twelve-line script.
- **Strict status checks** (branch must be up to date with `main`). This is the setting that stops
  "main moved under the work": a pull request tested against yesterday's `main` has to be updated
  and re-tested before it can merge.
- **Code-owner review is not required.** `CODEOWNERS` is corrected so it stops lying — the owner
  handle and the admin-app path — but a solo maintainer cannot be required to review themselves.
  The second handle stays a placeholder because it is not known; the file says so.
- **Explicit job names**, so the ruleset references a deliberate string rather than whatever the
  job's YAML key happened to be.

## Implementation steps

1. Rewrite the three workflows: no `pull_request.paths`; a first step that decides `relevant`; every
   expensive step guarded by it; explicit `name:` on each job.
2. Correct `.github/CODEOWNERS`.
3. Add `.github/rulesets/main.json` in GitHub's ruleset export format.
4. Validate locally: YAML parses; governance script passes.
5. Human: import the ruleset (administrator action; see the `HUMAN_ACTION_REQUIRED` in the handoff).
6. Human: verify by attempting the push that should now fail.

## Verification

Before the rule (this branch):

```bash
python3 -c "import yaml,glob; [yaml.safe_load(open(f)) for f in glob.glob('.github/workflows/*.yml')]"
python3 scripts/validate_repo_governance.py
```

After the rule (administrator, once imported):

```bash
git push origin main            # must be refused: "protected branch"
gh pr checks <docs-only PR>     # or the PR page: all three checks green, none "expected"
```

The second line is the one that proves trap 1 is closed. If any check reads "Expected" on a
docs-only pull request, the change-detection step is wrong and the rule must be relaxed until it is
fixed — a rule that blocks every pull request will be switched off, and then it protects nothing.

## Risks and rollback/forward-fix

- **The ruleset JSON schema is reconstructed from GitHub's export format, not round-tripped.** If the
  import rejects it, the settings are few enough to apply by hand from the Decisions section; the
  file is then replaced with a real export so the next import works.
- **Change detection compares against the pull request base.** A pull request opened before a
  workflow file changed will be evaluated by the new workflow, which is the correct behaviour but
  can surprise. `fetch-depth: 0` is required for the diff and makes checkout slower on this
  repository's history; acceptable.
- **`cancel-in-progress` plus strict checks** means a rebase cancels the running check and starts
  over. Correct, but it lengthens the loop on a busy branch.
- **Rollback** is disabling the ruleset in the UI; nothing in the repository breaks when it is off.

## Progress log

- 2026-09-22: plan written from the closed non-goal of plan 021; workflows, CODEOWNERS and the
  ruleset file implemented on `chore/022-branch-protection`; administrator import pending.

## Final outcome

Pending.

# Task handoff

## Objective

Plan 022: protect `main` so nothing reaches it without a pull request whose required checks passed
on the merged commit — making the review loop `CONTRIBUTING.md` describes a gate instead of a
post-mortem.

## Active branch

`chore/022-branch-protection`, branched from `main` at `5f9bb95`.

## Related issue or plan

`docs/plans/022-branch-protection.md`, the named non-goal of plan 021.

## Current status

ready_for_review — the in-repository half is complete and verified; the GitHub half is an
administrator action that no agent may perform. Both are below.

## Completed work

- **The three workflows always report.** `pull_request.paths` filters are gone; each job diffs the
  pull request against its base and skips its expensive steps when nothing relevant changed. This
  closes the trap that would otherwise block every docs-only pull request forever ("Expected —
  waiting for status"). The regexes were exercised against real history: a backend fix fires
  backend only, a docs commit fires neither, the OpenAPI contract fires both, and editing a workflow
  file fires its own check.
- **Explicit job names** (`governance`, `backend`, `frontend`) — the strings the ruleset references.
- **`.github/rulesets/main.json`**: pull request required, zero approvals, strict up-to-date checks,
  conversation resolution, no force-push, no deletion, **empty bypass list**.
- **`CODEOWNERS` corrected** — owner handle from the remote, `apps/admin/` → `apps/web/`. Code-owner
  review is *not* required by the ruleset; the file is fixed so it stops being wrong, not because it
  is enforced.

## Remaining work

The administrator action, then the verification that proves both traps are closed.

```text
HUMAN_ACTION_REQUIRED
Command: GitHub → lestatis/geo-housing-reviews → Settings → Rules → Rulesets → New ruleset
         → "Import a ruleset" → select .github/rulesets/main.json → Create
Reason: branch protection is a repository setting only an administrator can change; no agent may
        alter it, and the GitHub CLI is not installed or permitted here.
Expected result: a ruleset named "main", enforcement Active, with four rules and no bypass actors.
Verification command: git push origin main   (from any branch; must be refused as a protected
        branch)  — then open a docs-only pull request and confirm all three checks report green,
        none reading "Expected".
```

**Merge this branch first, then import.** The ruleset requires the three job names this branch
introduces; importing it against the old workflows would block every pull request until this one
merges — and this one could not merge either.

## Decisions made

- Zero required approvals: one maintainer, and GitHub does not count self-approval. The rule
  enforces process, not a second reviewer. One-line change when a second maintainer exists.
- Inline `git diff` for change detection rather than a marketplace action — twelve lines, no
  dependency.
- Governance runs in full every time (seconds); it is the check that notices a pull request editing
  the rules themselves.
- The `codex review` CI gate is deferred, with its cost and secret requirement noted in the plan.

## Changed files

`.github/workflows/{backend,frontend,governance}-check.yml`, `.github/CODEOWNERS`,
`.github/rulesets/main.json`, `.github/rulesets/README.md`, `docs/plans/022-branch-protection.md`,
this handoff.

## Commands and tests

```bash
./scripts/check-scoped.sh governance          # passed
python3 -c "import yaml,glob; [yaml.safe_load(open(f)) for f in glob.glob('.github/workflows/*.yml')]"
```

The workflows themselves are exercised by CI on this pull request — which is the first pull request
to run under the always-report shape, and therefore the real test of it.

## Failures and blockers

None. Pushing needs credentials this session does not have (HTTPS remote, no helper).

## Unresolved risks

- The ruleset JSON is written to GitHub's export schema from documentation, not round-tripped from
  a real export. If the import rejects it, apply the four settings by hand from the plan's Decisions
  and replace the file with a genuine export.
- `fetch-depth: 0` slows checkout; the diff needs history.
- Once enforcement is on, the next agent to try `git push origin main` gets refused — which is the
  point, and also the first thing that will look like a bug to whoever has not read this.

## Next action

Push, open the pull request, merge it, import the ruleset, run the verification push.

## Last updated

2026-09-22

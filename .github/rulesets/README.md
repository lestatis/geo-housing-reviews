# Rulesets

`main.json` is the branch protection for `main`, in GitHub's ruleset export format, so the rule
lives in the repository where it can be diffed and reviewed instead of only in the settings UI.

Apply it: **Settings → Rules → Rulesets → New ruleset → Import a ruleset**, choose this file.
Re-apply the same way after changing it. Plan 022 records what each setting is for and why the
bypass list is empty — an administrator who can push around the rule is the situation the rule
exists to end.

`integration_id: 15368` is GitHub Actions. The three `context` values are the `name:` of each
workflow's job; renaming a job without updating this file leaves the check permanently unsatisfied.

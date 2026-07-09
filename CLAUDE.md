@AGENTS.md

# Claude Code-specific instructions

- Use plan mode before implementation when a task changes more than one domain module, affects authentication/authorization, verification, moderation, ranking, migrations, or public API contracts.
- Prefer the project skills in `.claude/skills/` instead of repeatedly loading long procedures into chat.
- Use specialized read-only subagents in `.claude/agents/` for independent analysis. The primary agent remains responsible for the final decision and implementation.
- Before finishing, run the `finish-task` skill or follow its checklist manually.
- When corrected twice about the same repository-specific behavior, propose a concise update to `AGENTS.md`, a scoped `.claude/rules/*.md`, or a skill. Do not expand persistent instructions for one-off facts.
- Keep handoff summaries concise: objective, changed files, decisions, tests, risks, next action.
- Do not use `bypassPermissions` for this repository.
- Privileged-command handling is enforced by `.claude/hooks/block-privileged-commands.sh`; follow the human handoff format rather than looking for a workaround.

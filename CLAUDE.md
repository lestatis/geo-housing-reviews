@AGENTS.md

# Claude Code-specific instructions

- Use plan mode before implementation when a task changes more than one domain module, affects authentication/authorization, verification, moderation, ranking, migrations, or public API contracts.
- Prefer the project skills in `.claude/skills/` instead of repeatedly loading long procedures into chat.
- Use specialized read-only subagents in `.claude/agents/` for independent analysis. The primary agent remains responsible for the final decision and implementation.
- Before finishing, run the `finish-task` skill or follow its checklist manually.
- When corrected twice about the same repository-specific behavior, propose a concise update to `AGENTS.md`, a scoped `.claude/rules/*.md`, or a skill. Do not expand persistent instructions for one-off facts.
- Treat `AGENTS.md` as the shared source of truth for work performed by Claude Code and Codex.
- For long or multi-step tasks, maintain the current repository handoff incrementally at meaningful checkpoints; update it before intentionally pausing.
- When the user requests a switch to Codex, leave a usable handoff that reflects actual Git and test state. Do not claim reliable advance detection of a usage-limit interruption.
- Do not install or invoke unavailable Codex tooling. When a manual Codex command is required, use the `HUMAN_ACTION_REQUIRED` format from `AGENTS.md`.
- Do not use `bypassPermissions` for this repository.
- Privileged-command handling is enforced by `.claude/hooks/block-privileged-commands.sh`; follow the human handoff format rather than looking for a workaround.

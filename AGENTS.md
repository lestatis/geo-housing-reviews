# AGENTS.md

## 1. Project mission

Build a trustworthy Georgian housing-review platform. Users review buildings, residential complexes, and living experience. Verified experience is ranked above unverified content, but verification is never presented as proof that every statement is true. A listings marketplace is a future module, not part of the initial core.

## 2. Sources of truth

Read only what the task requires:

- Product scope: `docs/PRD_MVP.md`, `docs/MVP_SCOPE.md`
- Architecture: `docs/ARCHITECTURE.md`, accepted ADRs in `docs/adr/`
- Domain rules: `docs/DOMAIN_MODEL.md`, `docs/TRUST_VERIFICATION.md`, `docs/MODERATION.md`
- API: `docs/API_GUIDELINES.md`
- Security/privacy: `docs/SECURITY_PRIVACY.md`
- Delivery workflow: `docs/AI_WORKFLOW.md`, `CONTRIBUTING.md`, `PLANS.md`

Do not read every document by default. Start from this routing list and expand only when needed.

## 3. Mandatory working agreements

1. One task must have one explicit objective and acceptance criteria.
2. Inspect existing code and documentation before editing.
3. For multi-module, security-sensitive, migration, or unclear work, write/update an execution plan using `PLANS.md` before implementation.
4. Prefer the smallest correct change. Do not perform unrelated cleanup.
5. Do not introduce a production dependency, external service, new module boundary, or public API without explaining the trade-off and updating the relevant ADR/documentation.
6. Preserve the modular-monolith boundaries. No direct cross-module database access. Communicate through public application interfaces or domain events.
7. OpenAPI is the client contract. Never hand-maintain duplicated request/response types in clients when generation is available.
8. Database schema changes require a migration, rollback/forward-fix notes, and integration tests.
9. Security, moderation, verification, ranking, and personal-data changes require explicit negative-path tests.
10. Never commit secrets, access tokens, real identity documents, real lease agreements, production exports, or personal data.

## 4. Privileged commands and human handoff

Never run or work around commands requiring `sudo`, `su`, `doas`, `pkexec`, system package installation, writes to system directories, password prompts, or permission escalation.

When such an action is necessary:

1. Stop that path.
2. Output a block titled `HUMAN_ACTION_REQUIRED`.
3. Provide the exact command, reason, expected result, and verification command.
4. Wait for the human to confirm completion in a later turn.

Do not bypass this by changing ownership/permissions, using `chmod 777`, installing into an unexpected location, downloading an alternative binary, using a container, or silently choosing another implementation unless the task explicitly authorizes that alternative.

## 5. Product safety rules

- Treat uploaded verification evidence as highly sensitive.
- Store evidence only when necessary, encrypted, access-controlled, audited, and automatically deleted according to retention policy.
- Public reviews must not expose apartment numbers, personal phone numbers, identity numbers, document numbers, faces without consent, or information identifying uninvolved neighbours.
- “Verified” means the relationship or experience was checked, not that the review’s claims are certified as true.
- Preserve dispute, appeal, moderation, and audit trails.
- Ranking must not silently suppress critical reviews because a property owner pays or advertises.

## 6. Definition of done

A change is done only when:

- acceptance criteria are satisfied;
- relevant tests pass;
- formatting/lint/static checks pass;
- security and privacy implications were considered;
- API/schema/docs are updated where behavior changed;
- no unrelated diff remains;
- the PR description explains what changed, why, tests, risks, and follow-ups;
- unresolved uncertainty is stated explicitly, not hidden.

Run `./scripts/check.sh` before requesting review. Once code exists, use the repository-provided build commands documented in each app directory.

## 7. Review expectations

Reviewers prioritize: correctness, authorization, privacy, abuse paths, data loss, migration safety, concurrency, ranking integrity, missing tests, and maintainability. Report findings with severity, evidence, affected path, and a concrete fix. Do not generate style-only noise.

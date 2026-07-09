---
name: security-privacy-review
description: Review a change for authorization, privacy, abuse, auditability, secrets and data-retention risks.
---

# Security and privacy review

Read `docs/SECURITY_PRIVACY.md`, `docs/TRUST_VERIFICATION.md`, and `docs/MODERATION.md` as applicable.

Check:

- authentication and object-level authorization on every read/write path;
- tenant/user identifier guessing and enumeration;
- upload validation, metadata stripping and signed access;
- leakage through logs, errors, analytics, exports, notifications and caches;
- verification evidence minimization, purpose limitation, retention and deletion;
- moderator/admin privilege separation and immutable audit events;
- abuse: brigading, duplicate accounts, retaliation, doxxing, extortion and owner manipulation;
- rate limits, replay/idempotency and expensive endpoints;
- secret handling, dependency/configuration risk and unsafe defaults.

Report evidence and concrete mitigations. Escalate legal interpretation and public-policy choices to humans.

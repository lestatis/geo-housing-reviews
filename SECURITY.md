# Security Policy

## Reporting

Do not open a public issue for a vulnerability involving authentication, authorization, personal data, verification documents, moderation bypass, location leakage, or access to private media.

Until a private disclosure channel is configured, contact the repository owners directly through a private channel and include:

- affected component;
- reproduction steps;
- impact;
- proof of concept without real personal data;
- suggested mitigation, if known.

## Sensitive areas

The highest-risk areas are:

- identity and session management;
- access control for moderation/admin functions;
- uploaded evidence and media;
- review anonymity and location privacy;
- object-level authorization;
- ranking manipulation and paid influence;
- mass scraping, spam, brigading, and doxxing;
- audit log integrity;
- data export/deletion workflows;
- listings fraud when that module is introduced.

## Baseline rules

- Deny by default.
- Validate server-side; clients are untrusted.
- Use short-lived signed URLs for private media.
- Strip image metadata before durable storage.
- Never log tokens, full documents, personal numbers, or raw authentication payloads.
- Encrypt data in transit and at rest.
- Use least-privilege service credentials.
- Rate-limit mutation and search endpoints according to abuse risk.
- Require step-up authentication for sensitive moderator/admin actions.
- Preserve immutable security and moderation audit events.

See `docs/SECURITY_PRIVACY.md` and `docs/runbooks/INCIDENT_RESPONSE.md`.

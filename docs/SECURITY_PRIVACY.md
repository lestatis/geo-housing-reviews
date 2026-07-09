# Security and Privacy

This document is an engineering baseline, not legal advice. Obtain Georgian legal review before public launch, especially for content disputes, verification evidence and cross-border processing.

## 1. Data classes

### Public

Property information, sanitized public media, published review content, public badges and representative replies.

### Internal

Moderation metadata, risk signals, operational metrics, non-public property source notes.

### Confidential personal data

Account email, authentication identifiers, reports, private support messages, approximate location signals.

### Highly sensitive

Verification documents, identity/ownership/lease evidence, private media, raw personal numbers, security audit evidence.

Highly sensitive data must be isolated, encrypted, access-controlled, audited and retained for the shortest justified period.

## 2. Privacy principles

- purpose limitation;
- data minimization;
- clear notice at collection;
- configurable retention;
- user access/correction/export/deletion workflows;
- human review/appeal for consequential automated decisions;
- privacy by default in UI;
- no reuse of verification documents for marketing or unrelated analytics.

Georgia’s current personal-data protection law establishes rights and controller obligations. Exact applicability, lawful bases, notices, processors and cross-border transfers need legal confirmation. See `docs/REFERENCES.md`.

## 3. Threat model highlights

### External attacker

Targets accounts, admin access, evidence storage, signed URLs, API scraping and injection.

### Malicious reviewer

Fabricates relationship, posts personal data, coordinates campaigns, uploads malicious media or evades bans.

### Malicious representative

Attempts to identify critics, suppress reviews, manipulate ranking or abuse takedown channels.

### Insider/moderator

Accesses documents without need, leaks identity, changes decisions or colludes with property interests.

### Automated scraper

Builds profiles, harvests contact/location data or copies content.

## 4. Controls

- modern OAuth/OIDC provider; no custom password storage unless strongly justified;
- MFA/step-up auth for moderators/admins;
- RBAC plus object-level checks;
- short session/token lifetimes and revocation strategy;
- CSRF protection where cookies are used;
- secure headers and strict CORS;
- input validation and output encoding;
- upload quarantine and re-encoding;
- secrets manager in production;
- dependency and container scanning;
- database backups encrypted and restore-tested;
- rate limiting and abuse detection;
- append-only audit log with restricted access;
- periodic access review for moderators;
- deletion jobs with evidence of completion.

## 5. Logging

Never log:

- access/refresh tokens;
- passwords or auth codes;
- full review evidence;
- personal identification numbers;
- full lease/registry documents;
- raw signed URLs;
- exact location history;
- private moderation notes in analytics.

Use stable internal IDs and structured event names.

## 6. Retention

Define a retention registry before launch:

| Data | Purpose | Suggested policy direction |
|---|---|---|
| Account | service access | while active + justified closure period |
| Published review | public contribution | until deletion/removal, with lawful archival/audit limits |
| Raw verification evidence | relationship check | shortest period after decision/appeal |
| Verification decision metadata | trust/audit | longer than raw evidence, minimal fields |
| Moderation/audit events | safety and claims | policy/legal period |
| Operational logs | reliability/security | short rolling period |
| Backups | disaster recovery | finite rolling schedule with deletion propagation |

Exact periods require legal and operational approval.

## 7. Data subject requests

Provide authenticated workflows for access, correction, export, objection/appeal where applicable, and deletion. Verify requester identity without collecting excessive new data. Track deadlines and exceptions. See `docs/runbooks/DATA_SUBJECT_REQUEST.md`.

## 8. Content and defamation risk

Engineering should support:

- clear separation of opinion and factual claim prompts;
- dates and first-hand context;
- versioning and evidence references;
- notice-and-action workflow;
- right of reply;
- temporary restriction where necessary;
- legal hold without exposing content;
- reasoned decisions and appeals.

Do not build automated “truth scores” for allegations in MVP.

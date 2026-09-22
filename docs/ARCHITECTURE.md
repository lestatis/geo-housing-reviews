# Architecture

Status: Proposed baseline. Ratify or change through ADRs.

## 1. Architectural style

Use a **modular monolith** for the backend and a monorepo for the product. This minimizes operational overhead while preserving explicit domain boundaries that can be extracted later if usage proves the need.

No microservices in MVP.

## 2. Proposed stack

### Backend

- Java 21+ LTS baseline;
- Spring Boot;
- Spring Security;
- PostgreSQL;
- PostGIS for geo queries;
- Flyway for migrations;
- OpenAPI for external contracts;
- Testcontainers for integration tests;
- an outbox pattern when reliable asynchronous integration becomes necessary.

### Clients

- React Native + Expo for iOS/Android (`apps/mobile`, expo-router — see its README);
- Next.js for the admin web app (`apps/web`, App Router — see its README);
- generated API client from OpenAPI;
- shared design tokens, not shared UI components across incompatible platforms unless value is proven.

The OpenAPI document is exported to `docs/api/openapi.json` and pinned by
`OpenApiContractIntegrationTest`, so a contract change fails the backend build unless the file is
regenerated in the same commit. Clients generate from that file; no client hand-writes a request or
response type (AGENTS.md §3.7).

The admin app calls the API from the server and keeps the access token in an httpOnly cookie, so no
token reaches browser JavaScript and the API opens no CORS surface for the admin origin.

### Infrastructure

- Docker Compose for local dependencies;
- S3-compatible object storage;
- managed PostgreSQL in production;
- CDN for public sanitized media;
- queue, Redis and dedicated search engine only after measured need;
- Terraform after cloud/provider decision.

## 3. Backend modules

```text
identity
properties
reviews
verification
moderation
media
search
notifications
analytics
listings        # future, disabled/not implemented in MVP
shared-kernel   # tiny: identifiers, clock, domain event abstractions
```

### Boundary rules

- each module owns its tables, its migrations namespace, and its migration history;
- no module reads another module’s tables directly;
- public application interfaces are explicit;
- cross-module events contain stable identifiers and minimal data;
- domain entities are not shared between modules;
- `shared-kernel` must not become a dumping ground;
- listings references `property_id` through a public contract, not internal property entities.

The module dependencies that exist today, all one-way and acyclic, all reaching only the target's
`api` package (enforced by `ModuleBoundaryArchitectureTest`):

| From | To | Why |
| --- | --- | --- |
| reviews | properties | a review points at a property, and asks whether it takes reviews |
| reviews | identity | a restricted author may not submit or edit |
| moderation | reviews | a decision about a review has to reach the review |
| moderation | identity | a restricted account may not report; `RESTRICT_ACCOUNT` restricts the author |
| verification | reviews | an approved case projects a tier onto the author's reviews |

`identity` depends on no module, which is what keeps this acyclic: everything that needs to know
whether an account may act asks identity, and identity never learns who asked.

## 4. Suggested package layout

```text
com.example.geohousing
├── identity
│   ├── api
│   ├── application
│   ├── domain
│   └── infrastructure
├── properties
├── reviews
├── verification
├── moderation
├── media
└── shared
```

Use package-private visibility where possible. Module architecture tests should forbid accidental dependencies.

## 5. Data ownership

### Identity

Accounts, auth subject mapping, public profile, roles, restrictions, consent/notice versions.

### Properties

Canonical objects, aliases, addresses, coordinates, hierarchy, merge history, source provenance.

### Reviews

Review aggregate, versions, category ratings, publication state, helpful signals, ranking inputs.

### Verification

Verification case, method, evidence metadata, decision, expiration, public badge projection. Raw evidence is isolated from review content.

### Moderation

Reports, cases, decisions, appeals, representative replies, sanctions, audit projections.

### Media

Upload sessions, scans, metadata stripping, storage keys, visibility, deletion lifecycle.

## 6. Consistency

Use local ACID transactions inside a module. Cross-module workflows use application orchestration initially. Introduce transactional outbox only for operations that must survive retries across async boundaries.

Examples:

- publishing review updates review state and records an event;
- moderation consumes/handles review state through public application interfaces;
- media deletion is an idempotent job with audit events.

## 7. API boundaries

- public mobile API;
- admin API separated by authorization and ideally route namespace;
- no direct database access from clients;
- cursor pagination for feeds;
- idempotency keys for risky/retriable mutations;
- optimistic locking/version fields for moderator edits;
- Problem Details-compatible error body;
- explicit locale and timezone handling.

## 8. Search and geo

Start with PostgreSQL:

- trigram/full-text search for aliases and address fragments;
- PostGIS distance/bounding-box queries;
- normalized multilingual aliases;
- deterministic duplicate candidates.

Move to OpenSearch/Elasticsearch only when query quality, scale or analytics requirements justify operational cost.

## 9. Media pipeline

1. Client requests upload session.
2. Upload to private quarantine bucket/path.
3. Validate size/type and malware-scan when available.
4. Decode/re-encode image, strip EXIF/GPS.
5. Generate derivatives.
6. Move sanitized variants to appropriate visibility.
7. Delete quarantine original on schedule.

Verification evidence remains private and follows a stricter separate pipeline.

## 10. Observability

- structured logs with correlation/request ID;
- metrics for auth, moderation queue, upload failures, ranking errors, deletion jobs;
- traces for slow paths;
- audit events separate from operational logs;
- never place review evidence or personal data in logs.

## 11. Evolution to listings

The future listings module should add:

- listing, offer/price history, media, contact channel, source, owner/agent identity and fraud signals;
- immutable link to canonical property;
- independent ranking from reviews;
- clear sponsored labels;
- no ability to pay to remove or suppress reviews.

Do not add listing fields to review/property aggregates prematurely.

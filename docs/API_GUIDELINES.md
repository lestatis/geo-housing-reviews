# API Guidelines

## General

- JSON over HTTPS.
- OpenAPI is the source of truth for mobile/admin clients.
- The generated OpenAPI document is public at `/v3/api-docs` (JSON) and `/v3/api-docs.yaml`;
  interactive Swagger UI is public at `/swagger-ui/index.html`. These documentation routes do not
  make any application or actuator endpoint public.
- Version by compatible evolution first; use explicit major version only for unavoidable breaking changes.
- Use opaque IDs, not sequential identifiers exposed as authority.
- Server is authoritative for permissions, moderation and ranking.

## Resource style

Examples:

```text
GET    /api/properties
POST   /api/properties
GET    /api/properties/{propertyId}
GET    /api/properties/{propertyId}/reviews
POST   /api/properties/{propertyId}/reviews
GET    /api/reviews/{reviewId}
POST   /api/reviews/{reviewId}/reports
POST   /api/reviews/{reviewId}/helpful-signals
POST   /api/verifications
GET    /api/me/verifications/{caseId}
```

Admin endpoints use `/api/admin/...` and distinct authorization policies.

## Errors

Use a Problem Details-style structure:

```json
{
  "type": "https://example.invalid/problems/review-needs-changes",
  "title": "Review requires changes",
  "status": 422,
  "code": "REVIEW_PERSONAL_DATA_DETECTED",
  "detail": "Remove the apartment number before submitting.",
  "traceId": "...",
  "fieldErrors": [
    {"field": "cons", "code": "PERSONAL_DATA"}
  ]
}
```

Do not expose stack traces, SQL, internal policy scores or sensitive moderation signals.

## Pagination

Use cursor pagination for reviews, moderation queues and audit events. Responses include `nextCursor` and stable sort semantics.

## Idempotency

Require `Idempotency-Key` for operations such as:

- final review submission;
- verification submission;
- moderation decision;
- media finalization;
- data export/deletion request.

Store result and reject incompatible reuse.

## Concurrency

Use version/ETag or explicit `version` for mutable resources. Return conflict when a moderator acts on stale content.

## Localization

- request locale via standard headers/profile;
- preserve original review language;
- translations are separate projections with provider/version;
- never overwrite original text with machine translation;
- addresses and aliases support locale-specific forms.

## Security

- object-level authorization on every ID-based endpoint;
- no trust in client-supplied role, verification status or moderation state;
- signed upload URLs constrained by key, size, MIME and expiration;
- private media served through authorization, not public predictable URLs;
- rate limits based on endpoint abuse risk;
- audit sensitive admin mutations.

## Compatibility

Additive fields should be optional for clients. Removing/renaming fields, changing enum semantics or tightening validation requires migration planning and a compatibility window.

# Domain Model

This is a conceptual model. Database details belong in migrations and schema documentation after implementation.

## Property context

### Property

Represents a reviewable physical object.

Key fields:

- `property_id` — opaque stable identifier;
- `type` — building, residential_complex, block, phase;
- `status` — draft, active, merged, hidden;
- `canonical_name`;
- `address_id`;
- `geo_point`;
- `parent_property_id` — optional hierarchy;
- `merged_into_property_id`;
- `created_at`, `updated_at`.

### PropertyAlias

Multilingual name/address alias with locale, source and confidence.

### Address

Structured country/city/district/street/building fields plus original text. Avoid assuming every Georgian address fits one rigid pattern.

### PropertySource

Records where information came from and when it was observed. It does not grant truth automatically.

## Review context

### Review

Stable aggregate identity and current lifecycle state.

- `review_id`;
- `property_id`;
- `author_id`;
- `relationship_type`;
- `residence_period`;
- `status`;
- `current_version_id`;
- `verification_summary`;
- `published_at`;
- `moderation_version`;
- `ranking_version`.

Removal and rejection are terminal for every ordinary path. The single exception is reinstatement
after an appeal overturns the decision that took the review down (`P-014`), which is audited like any
other moderation action.

Rejection and removal are terminal for every ordinary path. The single exception is reinstatement
after an appeal overturns the decision that took the review down (`P-014`), audited like any other
moderation action and reachable only through the moderation module's appeal path.

### ReviewVersion

Immutable content version:

- localized original text;
- pros/cons;
- recommendation;
- category ratings;
- media references;
- edit reason;
- created timestamp.

### CategoryRating

Category identifier, value, `not_applicable`, optional note. Category definitions are versioned so historical ratings remain interpretable.

### HelpfulSignal

User signal with anti-abuse metadata. Do not expose raw voter identities publicly.

## Verification context

### VerificationCase

A private workflow linking account, property, relationship claim and method.

- status: pending, approved, rejected, expired, cancelled;
- method;
- requested claim;
- decision reason code;
- evidence retention deadline;
- reviewer and timestamps;
- public badge projection.

### VerificationEvidence

Private metadata and storage reference. Raw data must not leak into review APIs or logs.

### VerificationBadge

Public safe projection:

- type;
- verified-at;
- valid-through, if relevant;
- explanation key;
- no document number, apartment number or exact private address.

## Moderation context

### ModerationCase

Target, trigger, risk flags, assigned moderator, state, SLA timestamps.

### ModerationDecision

Immutable decision with reason code, free-text internal note, user-visible explanation, affected version and policy version.

### Report

Reporter, target, category, description, evidence references and abuse metadata.

### Appeal

Appeal text, prior decision, new evidence, outcome, separate reviewer when possible.

### RepresentativeClaim

A claim to represent a property/developer/management company. Verification is separate from resident review verification.

### RepresentativeReply

Public reply linked to review, versioned and moderated. It does not alter the review score.

## Media context

### MediaAsset

Owner, purpose, visibility, quarantine/sanitized storage keys, mime type, dimensions, scan status, metadata-stripped flag and retention state.

## Identity context

### Account

Private authentication identity and status.

### PublicProfile

Pseudonym, avatar, locale, public contribution stats. Do not expose email or legal identity.

### UserRestriction

Scope, reason, start/end, moderator and appeal status.

## Future listings context

### Listing

Property reference, seller/agent reference, offer type, status and source.

### ListingSnapshot

Price, area, rooms, description, media and observed timestamp. History is important because listings change.

### ContactChannel

Controlled contact method; avoid publishing raw personal contact data without explicit policy.

## Important invariants

- merged properties retain redirects and review history;
- only one current review version is displayed, but history is preserved internally;
- publication requires a moderation outcome according to current policy;
- a public verification badge can exist only for an approved, non-revoked case;
- deleting evidence does not delete the audit fact that verification occurred;
- moderator decisions are append-only; correction creates a new decision;
- paid status never mutates review verification or organic score.

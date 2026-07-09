# Trust and Verification

## 1. Principle

Verification answers: **“Do we have evidence that this account had the claimed relationship with this property?”**

It does not answer: **“Is every statement in the review objectively true?”**

The UI and copy must preserve this distinction.

## 2. Model

Do not use a single `verified=true/false`. Store:

- relationship claim;
- method;
- strength tier;
- verified timestamp;
- expiration/revocation;
- decision and policy version;
- public-safe badge.

## 3. Suggested tiers

### Tier 0 — Unverified

Account and basic anti-abuse checks only. Review may be published and remains searchable.

### Tier 1 — Weak relationship signal

Examples:

- repeated coarse location presence with explicit consent;
- invitation from an already verified resident;
- building-specific one-time code distributed through a trusted channel;
- email/phone domain or service signal when a legitimate partner exists.

This should not be labelled simply “verified resident”. Use a narrower label such as “location/relationship signal confirmed”.

### Tier 2 — Document-assisted relationship verification

Examples:

- lease fragment;
- utility bill;
- purchase/ownership extract;
- handover document;
- other document accepted by policy.

Minimize collection: request only the fields needed, support masking, and delete raw evidence quickly after decision.

### Tier 3 — Registry/partner-confirmed

Possible future method using an official or contractual data source. Availability, legality, API terms and data minimization must be validated before implementation.

## 4. Public badge types

Possible badges:

- `Verified current tenant`
- `Verified former tenant`
- `Verified owner`
- `Verified former owner`
- `Relationship signal confirmed`

Public copy must include a tooltip:

> The platform checked evidence of the reviewer’s relationship with this property. This does not certify every statement in the review.

## 5. Ranking

Verification contributes to rank but should not dominate all other factors.

Baseline idea:

```text
review_quality = completeness + specificity + moderation_confidence
trust_signal = verification_strength + account_integrity
relevance = property_match + time_relevance + user_filter_match
abuse_adjustment = campaign_risk + duplicate_risk + vote_manipulation
rank = f(review_quality, trust_signal, relevance, recency, diversity) - abuse_adjustment
```

Do not publish weights. Store ranking version and inputs needed for audit.

## 6. Evidence handling

- private quarantine storage;
- encryption and strict object-level authorization;
- access only for verification moderators;
- masked preview where possible;
- no raw evidence in logs, analytics or support tools;
- retention deadline set at upload;
- automatic deletion job with retries and audit;
- evidence access audit;
- explicit user notice and lawful basis/consent where applicable;
- user can cancel before decision unless another lawful retention basis applies.

Recommended target: delete raw evidence soon after final decision and appeal window, retaining only minimal decision metadata. Exact periods require legal review and should be configurable, not hard-coded.

## 7. Fraud and abuse

Signals may include:

- same document reused across unrelated accounts/properties;
- impossible residence periods;
- burst submissions from linked accounts;
- image/template similarity;
- mismatched property/address;
- repeated failed verification;
- moderator conflict of interest;
- compromised representative accounts.

Automated signals should prioritize cases, not silently make irreversible adverse decisions without review.

## 8. Revocation and expiration

A badge can be revoked for forged evidence, compromised account or moderator error. Current-resident badges may expire or become “verified former resident” after a policy-defined period.

Revocation should not automatically remove the review unless content policy requires it. The review can revert to unverified status.

## 9. Candidate Georgian sources

The National Agency of Public Registry provides real-estate registry and cadastral services, including property extracts. This may support manual verification or future integration, but availability of a machine API, permitted use, cost and privacy constraints must be confirmed before design.

See `docs/REFERENCES.md`.

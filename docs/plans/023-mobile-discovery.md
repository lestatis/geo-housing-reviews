# Plan 023 — mobile discovery: find a property, understand the experience

Status: Approved, ready for implementation — not started
Owner: Claude Code (lead) · implementation by a worker model
Related issue: None
Last updated: 2026-09-22

## Objective

A person in Batumi opens the mobile app, does not sign in, searches for a building or residential
complex, opens it, and reads its published reviews. MVP loop 1: **find property → understand
experience**.

The slice includes **two small backend contract changes** (an address summary on search hits, and
the removal of `authorAccountId` from public review responses). Both are accepted scope. Neither
requires a database migration, and neither changes module boundaries.

## Current system

Read before starting. These are facts verified against the code on 2026-09-22, not assumptions.

- **The three endpoints exist and are anonymous.** `SecurityConfiguration` permits
  `GET /api/properties/**` and `GET /api/reviews/**` without a token (P-011).
  - `GET /api/properties/search` — `q`, `lat`, `lng`, `radiusMeters`, `limit`. Returns
    `PropertySearchResponse { items }`. **No cursor and no total**: one page only.
  - `GET /api/properties/{propertyId}` — returns `PropertyResponse` with `address` (`AddressView`),
    `aliases`, `type`, `status`, coordinates, timestamps, `version`.
  - `GET /api/properties/{propertyId}/reviews` — `cursor`, `limit`. Returns
    `ReviewListResponse { items, nextCursor }`. Cursor pagination works.
- **The search query already joins the address table.** `SpringDataPropertyRepository` runs native
  SQL with `LEFT JOIN properties.address addr ON addr.id = p.address_id`, because it matches
  trigrams against `addr.street` and `addr.city`. Address columns are therefore available to the
  projection at **no extra query cost and with no N+1**.
- **Search deliberately includes `DRAFT`.** Only `HIDDEN` and `MERGED` are excluded; the repository
  javadoc explains why (a resident who adds a property must be able to find it again).
- **`ReviewResponse` is shared by the public and admin controllers.** `ReviewController` (public
  `GET /api/reviews/{reviewId}`, plus authenticated submit/edit) and `AdminReviewController` both
  return it. This is why removing a field is not a one-line deletion — see the contract change below.
- **No hand-written frontend code reads `authorAccountId`.** It appears in `apps/web` only inside the
  generated `src/api/generated/schema.d.ts`.
- **`apps/mobile` does not exist.** `pnpm-workspace.yaml` lists `apps/web` and `packages/*`.
  `ARCHITECTURE.md:27` already mandates React Native + Expo, so no ADR is needed to create it.
- **The root scripts are `pnpm -r lint|test|typecheck`**, so a new workspace member joins the L2 gate
  automatically — but `frontend-check.yml` decides whether to run from a regex listing `apps/web/`
  only. See 023-A.

## User journey

1. Opens the app. No account, no prompt to make one. A search field and a short statement of what
   the app is for.
2. Types "Abashidze", "Orbi", or a complex name.
3. Gets a ranked list of matching buildings, each showing its name and address.
4. Taps one.
5. Sees its name, aliases, address, type, and — when the record is thin — an honest notice saying so.
6. Reads the published reviews: relationship, period lived there, recommendation, category ratings,
   pros, cons, body.
7. Loads more reviews until there are none.
8. Loses signal mid-scroll, sees what failed, retries, continues from where they were.

At no point is an account required, offered as a gate, or implied.

## Screen inventory

| # | Screen | Route | Purpose |
| --- | --- | --- | --- |
| 1 | Home / search | `/` | Search entry and a plain-language statement of what the app is for |
| 2 | Results | `/search?q=` | Ranked matches with name + address, or a truthful empty state |
| 3 | Property detail | `/property/[id]` | Identity, address, data-sufficiency notice, then the review feed |

Three screens. Review detail and recent searches are follow-ups, not part of the slice.

## Navigation

`expo-router`, file-based stack. **No tab bar.** `DESIGN_HANDOFF.md` lists five primary tabs, four of
which are non-goals here; shipping dead or disabled tabs teaches the wrong thing about the product.

```
app/
  _layout.tsx           Stack + i18n provider + error boundary
  index.tsx             Home / search
  search.tsx            Results
  property/[id].tsx     Detail + review feed
```

`q` and `id` live in the route, not in component state, so screens are linkable and restorable. That
costs nothing now and is the difference between a shareable property link later and a rewrite.

## API operations per screen

| Screen | Operation | Parameters | Notes |
| --- | --- | --- | --- |
| Home | none | — | No network on launch. The first request is the user's. |
| Results | `GET /api/properties/search` | `q`, `limit=20` | `lat`/`lng`/`radiusMeters` exist and are **not** used — geolocation is a non-goal. |
| Detail (header) | `GET /api/properties/{propertyId}` | path id | |
| Detail (feed) | `GET /api/properties/{propertyId}/reviews` | path id, `limit=20`, `cursor` | `nextCursor` null ends the feed. |

The two detail requests fire in parallel; the header renders as soon as it lands rather than waiting
for the feed.

## Accepted backend contract changes

Both are contract-only. **Neither adds or alters a database table, so neither needs a Flyway
migration.** Both update `docs/api/openapi.json` in the same commit, regenerated with:

```bash
cd apps/api && ./gradlew :app:test --tests '*OpenApiContractIntegrationTest' -DupdateOpenApiSpec=true
```

### C1 — public address summary on search hits (in 023-B)

`PropertySearchHitResponse` currently carries `propertyId`, `canonicalName`, `score`,
`distanceMeters`. That cannot distinguish two similarly-named buildings, which is common in Batumi.

Add to the hit:

- `address` — a new `AddressSummaryView(city, district, street, building)`;
- `type` — the property type, as `PropertyResponse` already exposes it.

**Why a summary rather than reusing `AddressView`:** `originalText` is user-entered free text and is
the likeliest place an apartment number is hiding, and a result row does not need it; `country` is
effectively constant (`GE`). The summary is therefore *strictly less* than the public detail endpoint
already exposes, which satisfies the "nothing beyond public detail" rule by construction rather than
by review. `type` costs nothing extra — it is another column on the row already being read.

Carry the fields through the existing seam: `PropertySearchProjection` → `PropertyMatch` →
`PropertySearchHitResponse`. The SQL already joins `properties.address`; add the columns to the
`SELECT` list. **Do not add a join, a second query, or a per-hit detail fetch.**

`score` stays in the contract (an existing client concern) but the mobile app **must never render
it**.

### C2 — remove `authorAccountId` from public review responses (in 023-C)

The public feed hands every anonymous client a stable account UUID, which can be correlated across
properties into one person's review history. That conflicts with P-003 (pseudonym by default for
reviewers) and is unnecessary for any public screen.

`ReviewResponse` is shared by `ReviewController` (public) and `AdminReviewController`, so this is a
**split, not a field deletion**:

- public `ReviewResponse` — drop `authorAccountId`, add no replacement identifier;
- new `AdminReviewResponse` — same shape **plus** `authorAccountId`, returned by
  `AdminReviewController` only. This follows the existing `AdminAppealResponse` /
  `AdminAppealQueueResponse` precedent, so it is a convention already in the codebase.

Everything else stays: the `author_account_id` column, `ReviewJpaEntity`, `AuthorId`,
`ModeratableReview`/`ModeratableTarget`, and moderation's appeal and report ownership checks all use
internal ports, not this DTO. **Do not touch them.**

Known test to update: `ReviewEndpointIntegrationTest` asserts `$.authorAccountId` on the public
response; it becomes an assertion that the field is *absent*, and the admin coverage moves to the
admin DTO.

**Compatibility note.** `API_GUIDELINES.md` §Compatibility asks for migration planning and a
compatibility window on field removal. The window is satisfied here rather than skipped: the only
consumer is `apps/web`, where the field appears solely in the generated schema and no hand-written
code reads it, and the mobile app is unreleased. Record this reasoning in the pull request.

Both changes are founder decisions — add an entry for each to `docs/DECISION_LOG.md` using the next
free `P-0xx` numbers.

## Data mapping

Only these fields are read. Anything else the API returns is deliberately not rendered.

**Search hit → result row**
`canonicalName` → title · `address` → one line, "street building, district, city", blanks omitted ·
`type` → translated label · `propertyId` → route param · `score`, `distanceMeters` → **never
rendered**.

**Property → detail header**
`canonicalName` → title · `address.{street, building, district, city}` → address line;
`address.originalText` → fallback only when the structured parts are all empty · `aliases[].name` →
"also known as", de-duplicated against the title, preferring the user's `locale` · `type` →
translated label · `status` → drives the data-sufficiency notice · coordinates, `version`,
`parentPropertyId`, `mergedIntoPropertyId`, timestamps → not shown.

**Review → feed card**
`relationshipType` → translated label · `residenceFrom`/`residenceTo` → **years only** ("2023–2024"),
because a month plus a building narrows towards a person · `verificationTier` → badge worded per
`TRUST_VERIFICATION.md`: a checked *relationship*, never certified *claims* · `recommendation` →
translated label, never a bare colour · `content.ratings[]` → category chips from `category` and
`value`, skipping `notApplicable`, showing `note` when present · `content.pros`, `content.cons`,
`content.body` → text blocks · `content.locale` → language tag shown only when it differs from the UI
locale · `helpfulCount` → the count alone, never who voted · `publishedAt` → relative date ·
`status`, `version`, `updatedAt`, `content.versionNumber`, `categorySetVersion` → not shown.

## UI states

Every networked screen implements loading, empty, error and loaded. A screen that is only "spinner or
content" is incomplete and should fail review.

**Results**
- *Loading*: skeleton rows, announced to screen readers as "Searching".
- *Empty*: "No building found for «…»", plus what to try: spelling, the street instead of the
  building, the district. **No "add this property" call to action** — creating a property needs an
  account, so offering it would dead-end the user.
- *Error*: one sentence plus Retry, per the error model below.
- *Loaded*: rows. When exactly `limit` results come back, say "Showing the first 20" — search cannot
  page, and silence would imply the list is complete.

**Detail**
- *Loading*: header skeleton, then feed skeleton.
- *Header error*: the whole screen fails with Retry — without identity there is nothing to show.
- *Feed error after a successful header*: the header stays and the feed area carries its own Retry.
  **A failed next page must never discard the pages already loaded.**
- *Empty feed*: "No published reviews yet."
- *Paging*: an inline footer indicator **and** a "load more" button — scroll-triggered loading alone
  is unreachable for some assistive technologies.

## Error model

Timeout is not evidence of being offline, and the internal classification must not pretend otherwise.

| Outcome | Raised when |
| --- | --- |
| `notFound` | 404 |
| `offline` | **only** when the device reports no network (`expo-network` / NetInfo) |
| `timeout` | the 10 s request deadline elapsed |
| `server` | 5xx, or any other non-2xx |
| `malformed` | 2xx whose body does not match the generated type |
| `unknown` | anything else, treated as recoverable |

User-facing copy may fold `timeout`, `server` and `unknown` into one temporary-availability message;
only a genuine `offline` may say "No connection". `notFound` on a property reads "This building is no
longer listed" — a merged or hidden property is a normal outcome, not a crash.

Retry is manual and idempotent. **No automatic retry loops** on a metered mobile connection. No
message exposes a URL, a status code, a stack, or a raw server string.

## DRAFT and data sufficiency

`DRAFT` properties stay visible — the backend's catalogue visibility rules are preserved and the
client adds no filter. Because the app will therefore show buildings nobody has vetted, it must say
so:

- `status == DRAFT` → a low-confidence notice, in the spirit of "This property was added by a user
  and has not yet been reviewed by our team." Exact wording is a design/localization detail.
- zero reviews on the first page → "No published reviews yet."
- a full page plus a `nextCursor` → "Showing the first 20 reviews".

**The app must never print a review count it did not itself count**, and must not compute or display
any aggregate rating. No totals are available from the API, and none are to be inferred.

## Localization

- `en` and `ru` ship, matching P-002 (Russian and English active at launch). Georgian content is not
  solicited or published until a Georgian-reading moderator exists, so `ka` is a later UI question,
  not a gap in this slice.
- `expo-localization` reads the device locale; a manual override persists on the device. There is no
  account, so no server-side preference.
- Messages live in `src/i18n/<locale>.ts` as typed dictionaries with `en` as the source of truth and
  the type derived from it, so a missing Russian key is a compile error rather than a blank label.
- **Russian plurals need three forms** ("1 отзыв / 2 отзыва / 5 отзывов"). Verify in 023-A that the
  runtime provides `Intl.PluralRules` and `Intl.DateTimeFormat` for `ru`; if it does not, add the
  smallest helper covering `en` and `ru` and move on. This is a fifteen-minute check, not a project —
  it is in 023-A only because discovering it later means rewriting every count string.
- UI locale and content locale are independent: show a language tag when `content.locale` differs
  from the UI. No machine translation.
- No sentence is assembled by concatenation; every message is a whole phrase with named placeholders.

## Accessibility

Semantic requirements, not visual style. Treated as acceptance criteria.

- Touch targets ≥ 44×44 pt, including the locale switch and "load more".
- `accessibilityRole` and `accessibilityLabel` on every control; a result row reads as its name,
  address and type, and announces that it opens details.
- **Text scales**: no `allowFontScaling={false}` anywhere. The review card — badge, chips and three
  text blocks — is where this breaks, so it is checked at 200%.
- Verification and recommendation never rely on colour alone: icon plus text in every case.
- Loading and error states announce themselves, so a blind user learns the search finished.
- Body-text contrast ≥ 4.5:1 — a constraint on the palette Dasha chooses, stated here so it is not
  discovered in review.
- Reading order follows visual order; the badge is read after the name, not before it.
- Reduced-motion respected for any skeleton shimmer.

## Decisions

Accepted by the founder on 2026-09-22 and no longer open:

1. Search hits carry a public address summary (C1). Accepted scope change.
2. **No review counts or aggregate ratings** in this slice — not `reviewCount`, not
   `verifiedReviewCount`, not category aggregates. Only data the system truthfully has. Deferred to a
   follow-up, which must honour P-007's data-sufficiency threshold.
3. `authorAccountId` is removed from public review responses (C2). Accepted scope change. No public
   author name in this slice; attribution is relationship + verification.
4. `DRAFT` properties remain visible, with a low-confidence notice.
5. Stack navigation only; no tab bar, no dead tabs.
6. The slice stays anonymous: no login, no auth prompt, no `Authorization` header, no account or
   property creation, no review submission.
7. Timeout is classified separately from offline.

Lead decisions taken while writing this plan: the address summary omits `originalText` and `country`
(rationale under C1); `type` joins the hit in the same change; and `ReviewResponse` splits rather than
loses a field, following the `AdminAppealResponse` precedent (rationale under C2).

## Implementation steps

Three chunks. Each is a pull request, independently reviewable, and does not need the next one to be
useful.

### 023-A — mobile foundation

- Create `apps/mobile`: Expo + TypeScript + `expo-router` stack.
- Add `apps/mobile` to `pnpm-workspace.yaml`; add any Expo native postinstall to `allowBuilds` as a
  deliberate, reviewable line.
- Copy the `generate:api` script pattern from `apps/web` so types come from `docs/api/openapi.json`,
  and wire it ahead of `typecheck` and `test`.
- Public API client over `openapi-fetch`: base URL from config, 10 s timeout, the error union above,
  and **no `Authorization` header**.
- `en`/`ru` dictionaries, locale override persisted on the device, and the `Intl` check.
- Add `apps/mobile/` to `frontend-check.yml`'s relevance regex and push `paths`. Without this a
  mobile-only pull request skips the frontend job and **reports green having tested nothing**, while
  the root `pnpm -r` scripts would still run mobile's tests on any `apps/web` pull request. Two lines.
- Add the `apps/mobile` path to `ARCHITECTURE.md:27`, where Expo is already mandated.
- A smoke screen proving the app launches.

*Acceptance*: app launches on a simulator and a device; switching locale changes a visible string;
Russian 1/2/5 plural forms render correctly; the client is built from generated types with no
hand-written request/response type; each error-union branch is unit-tested, including a malformed
body and a timeout; a test asserts no `Authorization` header is ever attached; `pnpm -r test` from the
root visibly includes mobile; lint, typecheck and tests pass; a mobile-only branch triggers the
frontend check.

### 023-B — discovery

- Backend C1: address summary and `type` on the search hit, through the existing projection seam,
  plus the OpenAPI regeneration and a `DECISION_LOG.md` entry.
- Home/search screen and results screen, with loading, empty, recoverable error and loaded states.
- Result rows render name, address and translated type.

*Acceptance*: an anonymous search works end to end from the app; two properties with the same or
similar `canonicalName` are distinguishable by their address; the search issues **one** request per
query, verified by asserting no per-hit detail fetch; `score` appears nowhere in the rendered tree;
every state is test-covered; backend tests cover a hit with a full address, a hit with a partial
address, and a property with no address at all; `OpenApiContractIntegrationTest` passes against the
committed spec.

### 023-C — property experience

- Backend C2: split `ReviewResponse` / `AdminReviewResponse`, update the affected tests, regenerate
  OpenAPI, add a `DECISION_LOG.md` entry.
- Property detail header: name, aliases, address, type, DRAFT/data-sufficiency notice, the "no longer
  listed" 404 copy.
- Review feed: cursor paging, empty feed, a feed-level error that preserves loaded pages.
- Review card: relationship, residence years, verification badge, recommendation, category ratings,
  pros, cons, body, helpful count, and a language tag when the content locale differs.

*Acceptance*: anonymous property read and anonymous review feed both work from the app; **no stable
internal author identifier appears in any public response** — asserted on the API and again on the
rendered tree, worded as "no internal account identifier is exposed" so it survives a rename; admin
responses still carry `authorAccountId`; moderation's appeal and report ownership tests are untouched
and still pass; paging walks at least two pages and stops on a null `nextCursor`; a failed second page
leaves the first page on screen; the DRAFT notice appears for a DRAFT property; no total review count
or aggregate is displayed anywhere; the review card is checked at 200% text scaling on one device and
the evidence recorded in the pull request.

## Verification

- **Unit** (Jest + React Native Testing Library): every state of every screen, driven by a stubbed
  client. This is what makes the four-state rule real rather than aspirational.
- **Mapping**: fixture in, rendered text out — one per rule above, including the rules that assert a
  field is *absent*.
- **Contract**: types are generated from `docs/api/openapi.json`, so drift is a typecheck failure;
  `generate:api` runs before `typecheck` and `test`, so a stale client cannot pass.
- **Backend**: the two contract changes follow the repository's test-first rule and are covered by
  the module's own tests plus the endpoint tests named in each chunk's acceptance.
- **Localization**: every `en` key exists in `ru`; a Russian plural test with 1, 2 and 5.
- **Accessibility**: role and label assertions in component tests; text scaling and contrast checked
  manually on one device and recorded as chunk evidence.
- **No device e2e** in this slice.
- Use the repository's L0/L1/L2 levels. Do not run the full gate in the edit loop.

## Non-goals

Authentication and login · account or profile · review submission or editing · helpful voting
actions · verification flows · reports · appeals · evidence upload · maps · geolocation and
location-based search UI · saved properties · compare · notifications · representative features and
replies · property creation · review photos or media · machine translation · device E2E
infrastructure · new CI jobs · CI optimization · Testcontainers optimization · Terraform · Redis ·
OpenSearch · role management · backend redesign · unrelated backend cleanup · a tab bar for features
that do not exist.

The two-line CI path addition in 023-A is not an exception to "no CI changes": it is the minimum
needed for the existing gate to keep telling the truth once a second frontend app exists.

## Design decisions owned by Dasha

The plan defines semantics and states; it does not invent a visual style. Engineering is not blocked
waiting for polish, but these remain design's call:

verification badge treatment and its explanatory sheet · review-card visual hierarchy · category chip
and icon system · how prominent the DRAFT/data-sufficiency notice should be (noticeable without making
every new building look disreputable) · empty state with or without illustration · palette, meeting
the 4.5:1 body-contrast constraint · typography · spacing · the tone of a critical review, which
`DESIGN_HANDOFF.md` requires to read as information rather than sensation · whether a Georgian UI is
added later.

## Follow-ups

Recorded so they are not lost, and deliberately outside this slice:

1. **Public pseudonym projection** for review authors, replacing today's absence of any author
   identity (P-003).
2. **Review counts and aggregates** — `reviewCount`, `verifiedReviewCount` and a category summary,
   honouring P-007's minimum-review-count threshold before any single overall number is shown. This
   is what the `DESIGN_HANDOFF.md` property card needs and cannot have yet.
3. **Search pagination** — a cursor and a total on `/api/properties/search`, if real usage shows
   people hitting the 20-result ceiling.
4. **Review detail screen** and device-local recent searches.
5. **Device E2E** (Detox or Maestro) once there is a reason to pay for emulator infrastructure.

## Risks and rollback/forward-fix

- **`Intl` support in the React Native runtime** is the likeliest technical surprise; 023-A finds out
  early and the fallback is a small helper, not a redesign.
- **Expo native postinstalls** must be added to `allowBuilds` deliberately, one reviewed line each,
  not by a blanket allow.
- **Mobile joins the shared `pnpm -r` gate** the moment it is a workspace member, so a slow or flaky
  React Native test setup would slow `apps/web`'s pull requests too. Keep mobile's tests node-only.
- **`apps/web`'s client is Next-shaped**: the generator is reusable, the fetch layer is not. Copying
  `serverApi()` would drag in cookie and server-component assumptions that do not exist on a device.
- **Rollback**: both backend changes are contract-only with no schema change, so reverting the commit
  is a complete rollback. C1 is additive and safe to revert at any time. C2 removes a field, so a
  revert would re-expose `authorAccountId` — forward-fix is preferred, and the compatibility reasoning
  is recorded in its pull request.

## Progress log

- 2026-09-22: plan written from a contract inspection; four gaps and two product questions raised.
- 2026-09-22: revised after founder decisions. Address on search hits and removal of public
  `authorAccountId` are now accepted scope; counts/aggregates, pseudonym, search paging, review detail
  and recent searches moved to follow-ups; five chunks reduced to three; the error model now separates
  timeout from offline. Two errors in the first draft were corrected against the code and the decision
  log: removing `authorAccountId` is a DTO split rather than a field deletion, because
  `AdminReviewController` shares the record; and the claim that users would read Georgian review
  bodies contradicted P-002, under which Georgian content is not published at launch. Not implemented.

## Final outcome

Pending.

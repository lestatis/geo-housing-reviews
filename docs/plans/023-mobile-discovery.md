# Plan 023 — mobile discovery: find a property, understand the experience

Status: Proposed — not started
Owner: Claude Code (lead) · implementation suitable for a worker model
Related issue: None
Last updated: 2026-09-22

## Objective

A person in Batumi opens the mobile app, does not sign in, searches for a building or residential
complex, opens it, and reads its published reviews. MVP loop 1: **find property → understand
experience**.

## Can this be built on the current API?

**Yes.** Every screen below is served by three endpoints that already exist and are already
anonymous (`SecurityConfiguration` permits `GET /api/properties/**` without a token). No backend
change is required to ship the slice.

The contract was read rather than assumed, and it has four gaps worth knowing before design starts.
None blocks the slice; each forces a wording or scope decision, and two of them contradict
`docs/DESIGN_HANDOFF.md`. They are listed under **Decisions needed** with a recommendation each.

## User journey

1. Opens the app. No account, no prompt to make one. A search field and an explanation of what this
   app is for.
2. Types "Abashidze" or "ვაკე" or "Vake Heights".
3. Gets a list of matching buildings, newest-first by relevance score.
4. Taps one.
5. Sees its name, aliases, address, type, and how established the record is.
6. Scrolls into the published reviews: relationship, period lived there, recommendation, category
   ratings, pros, cons, body.
7. Loads more reviews until there are none.
8. Loses signal mid-scroll, sees what failed, retries, continues.

At no point is an account required, offered as a gate, or implied.

## Screen inventory

| # | Screen | Route | Purpose |
| --- | --- | --- | --- |
| 1 | Home / search | `/` | Search entry, plain-language explanation of the app, recent searches (device-local) |
| 2 | Results | `/search?q=` | Ranked matches, or a truthful empty state |
| 3 | Property detail | `/property/[id]` | Identity, address, aliases, data-sufficiency, then the review feed |
| 4 | Review detail (optional, chunk 6) | `/property/[id]/review/[reviewId]` | A single long review, readable and shareable |

Four screens. No tab bar in this slice: `DESIGN_HANDOFF.md` lists five primary tabs (Search/Map,
Saved, Add review, Notifications, Profile), and four of the five are non-goals here. Shipping a tab
bar with one live tab and four dead ones teaches the wrong thing about the product. A stack is
honest about what exists.

## Navigation structure

`expo-router`, file-based, chosen because the team already reasons in Next.js App Router routes and
the mapping is one-to-one.

```
app/
  _layout.tsx           Stack + i18n provider + query client + error boundary
  index.tsx             Home / search
  search.tsx            Results (q as a search param, so the screen is linkable and restorable)
  property/[id].tsx     Detail + review feed
```

Deep-linkable from the start (`q` and `id` in the URL, not in component state): it costs nothing now
and is the difference between a shareable property link later and a rewrite.

## API operations per screen

| Screen | Operation | Parameters | Notes |
| --- | --- | --- | --- |
| Home | none | — | No network on launch. The first request is the user's. |
| Results | `GET /api/properties/search` | `q`, `limit=20` | `lat`/`lng`/`radiusMeters` exist and are **not** used: geolocation and maps are non-goals. |
| Detail (header) | `GET /api/properties/{propertyId}` | path id | |
| Detail (feed) | `GET /api/properties/{propertyId}/reviews` | path id, `limit=20`, `cursor` | Cursor pagination; `nextCursor` null ends the feed. |

Both detail requests fire in parallel; the header renders as soon as it lands rather than waiting for
the feed.

## Data mapping

Only these fields are read. Anything else the API returns is deliberately not rendered.

**Search hit** → result row
`canonicalName` → title · `propertyId` → route param · `score`, `distanceMeters` → **not shown**
(score is an internal relevance number; distance is always null without a location, which is a
non-goal).

**Property** → detail header
`canonicalName` → title · `address.{street, district, city}` → subtitle, joined by the locale's list
separator, omitting blanks · `address.originalText` → fallback when the structured parts are empty ·
`aliases[].name` → "also known as", de-duplicated against the title, `locale` used to prefer the
user's language · `type` → translated label (`BUILDING` → "Building" / "Дом") · `status` → drives the
data-sufficiency notice (see below) · `latitude`/`longitude`/`version`/`mergedIntoPropertyId`/
`parentPropertyId`/timestamps → **not shown**.

**Review** → feed card
`relationshipType` → translated label ("Current resident") · `residenceFrom`/`residenceTo` →
"2023–2024", the year only, because a month plus a building is a step towards identifying a person ·
`verificationTier` → badge with the wording from `TRUST_VERIFICATION.md`: checked *relationship*, not
verified *claims* · `recommendation` → translated label, never a bare colour · `content.ratings[]` →
category chips using `category`, `value`, skipping `notApplicable`; `note` shown if present ·
`content.pros`, `content.cons`, `content.body` → text blocks · `content.locale` → language tag when
it differs from the UI locale · `helpfulCount` → count only, never who · `publishedAt` → relative
date · `authorAccountId` → **never rendered** (see Decisions needed #3) · `status`, `version`,
`updatedAt`, `content.versionNumber`, `categorySetVersion` → not shown.

## UI states

Every networked screen implements four states. A state that is only "spinner or content" is
incomplete and should fail review.

**Results**
- *Loading*: three skeleton rows, no spinner-on-blank. Announced to screen readers as "Searching".
- *Empty*: "No building found for «Abashidze»." Plus what to do: check spelling, try the street
  instead of the building, try the district. **No "add this property" call to action** — creating a
  property requires an account, which is a non-goal, and offering it would dead-end the user.
- *Error*: what failed, in one sentence, plus Retry. Distinguish offline ("No connection") from
  server ("Search is unavailable right now") because the user's next action differs.
- *Loaded*: rows; a note when exactly `limit` came back, since search cannot page (gap #4).

**Detail**
- *Loading*: header skeleton, then feed skeleton.
- *Header error*: the whole screen fails with Retry — without identity there is nothing to show.
- *Feed error after a successful header*: the header stays, the feed area carries its own Retry. A
  failed second page must never discard the pages already read.
- *Empty feed*: "No published reviews yet." For a `DRAFT` property, say why that is unsurprising.
- *Paging*: inline footer spinner; "load more" is also a button, not scroll-only, because
  scroll-triggered loading is unreachable for some assistive technologies.

## Data-sufficiency notice

`DESIGN_HANDOFF.md` requires a warning when a property has little data, and the trust model depends
on it. Search returns `DRAFT` properties to anonymous users on purpose (only `HIDDEN` and `MERGED`
are excluded), so the app will show buildings that nobody has verified and nobody has reviewed.

For this slice, with no counts available from the API (gap #2), the notice is driven by what the
client can honestly know:

- `status == DRAFT` → "This building was added by a resident and has not been reviewed by our team."
- zero reviews in the first page → "No published reviews yet."
- a full page plus `nextCursor` → "Showing the first 20 reviews", never a total.

The app must not print a review count it has not counted.

## Localization

Structural from the first commit, two locales shipped (`en`, `ru`), and a third anticipated.

- `expo-localization` reads the device locale; a manual override is persisted locally. No account, so
  no server-side preference.
- Messages live in `src/i18n/<locale>.ts` as a typed dictionary, with `en` as the source of truth and
  the type derived from it, so a missing Russian key is a compile error rather than a blank label.
- **UI locale and content locale are independent, and this matters here more than usual.** The market
  writes reviews in Georgian (`ka`) — the seeded fixtures are Georgian — while this slice ships `en`
  and `ru`. A Russian-speaking user will read Georgian review bodies inside a Russian interface. So:
  never assume `content.locale` matches the UI, label a review's language when it differs, and do not
  build machine translation (a Could-have).
- **Russian plurals are a real constraint, not a formatting detail.** "1 отзыв / 2 отзыва / 5
  отзывов" needs three forms. `Intl.PluralRules` is the correct tool and is **not reliably present in
  React Native's default Hermes build without full ICU**. Chunk 1 must verify this on a device and,
  if absent, either enable the ICU variant or add the smallest pluralisation helper that covers `en`
  and `ru`. Discovering this in chunk 5 would mean rewriting every count string.
- Dates and relative times through `Intl.DateTimeFormat`/`RelativeTimeFormat` with the same ICU
  caveat, and the same check in chunk 1.
- No string concatenation for sentences; every message is a whole phrase with named placeholders.

## Accessibility

Mobile-first and treated as acceptance criteria, not polish.

- Every touch target ≥ 44×44 pt, including the locale switch and "load more".
- `accessibilityRole` and `accessibilityLabel` on every control; result rows are `button`s that read
  as "Vake Heights, building, opens details".
- **Text scales.** No `allowFontScaling={false}` anywhere. Cards must survive the largest OS text
  size without clipping — the review card, with badge plus chips plus three text blocks, is where
  this will break, so it is tested at 200%.
- Verification and recommendation never communicate by colour alone: icon plus text in every case.
  A red "not recommended" chip that is only red is invisible to a colour-blind reader and to a
  screen reader both.
- Loading and error states announce themselves (`accessibilityLiveRegion` / `AccessibilityInfo`), so
  a blind user learns the search finished.
- Contrast ≥ 4.5:1 for body text; this constrains Dasha's palette and belongs in the design decision
  list rather than being discovered in review.
- `prefers-reduced-motion` respected for skeleton shimmer.
- Screen-reader reading order follows visual order; the badge is read after the name, not before it.

## Error handling

- One typed API client wrapping `openapi-fetch`, mapping every outcome to `ok | notFound | offline |
  server | malformed`. Screens branch on that union, never on a raw status code.
- 404 on a property → "This building is no longer listed", not a crash and not a generic error: a
  merged or hidden property is a normal outcome, and `mergedIntoPropertyId` exists precisely because
  records get merged.
- Timeouts: 10 s, then the offline copy. A hung request with a spinner forever is the worst of the
  available failures.
- Retry is manual and idempotent. No automatic retry loops on a metered mobile connection.
- No error message exposes a URL, a status code, a stack, or a raw server string.

## Test strategy

- **Unit** (Jest + React Native Testing Library): every state of every screen — loading, empty,
  error, loaded, paging — driven by a stubbed client. These are the tests that make the four-state
  rule real rather than aspirational.
- **Mapping** tests: an API fixture in, rendered text out. One per mapping rule above, including the
  ones that assert a field is *absent*: `authorAccountId` must not appear in the rendered tree, and
  neither must `score`. Written as "no internal identifier reaches the screen", so they keep meaning
  if the field is renamed.
- **Contract**: types come from `docs/api/openapi.json` via `openapi-typescript`, so a contract drift
  is a typecheck failure. `pnpm generate:api` runs before `typecheck` and `test`, exactly as
  `apps/web` does — a stale client cannot pass.
- **Accessibility**: assertions on role and label in the component tests; the text-scaling and
  contrast checks are manual, on one device each, recorded in the chunk's evidence.
- **Localization**: a test that every key in `en` exists in `ru`, and a Russian plural test with
  1/2/5 reviews.
- **No device e2e** (Detox/Maestro) in this slice. It needs an emulator in CI, which is the
  infrastructure work this plan is explicitly not starting. Recorded as a follow-up.
- **Mobile lands in CI automatically, and the trap is worth stating precisely.** The root scripts
  are `pnpm -r lint|test|typecheck`, so the moment `apps/mobile` is a workspace member with those
  scripts, the frontend job and the L2 gate run them — no wiring needed. But `frontend-check.yml`
  decides whether to run at all from a regex listing `apps/web/` and not `apps/mobile/`. So a
  **mobile-only pull request skips the frontend job and reports green having tested nothing**, while
  a pull request that also touches `apps/web` does run mobile's tests. A required check that passes
  without executing is worse than no check. Chunk 1 adds `apps/mobile/` to that regex and to the
  push `paths` — two lines, in the chunk that creates the app, not a follow-up.

## Implementation chunks

Sized for a worker model: each is independently verifiable, names its acceptance, and does not need
the next one to be reviewable. No chunk mixes scaffolding with product behaviour.

**Chunk 1 — the app exists and speaks two languages**
Expo + TypeScript + expo-router skeleton under `apps/mobile`; `apps/mobile` added to
`pnpm-workspace.yaml`; `generate:api` script copied from `apps/web`; i18n provider, `en`/`ru`
dictionaries, locale override; `apps/mobile/` added to `frontend-check.yml`'s relevance regex and
push paths; **the ICU/plural check on a device, resolved before the chunk closes**.
*Acceptance*: app launches on a device and a simulator; switching locale changes a visible string;
`1/2/5 отзывов` renders correctly; `pnpm --filter mobile typecheck` and `test` pass; **`pnpm -r test`
from the root includes mobile** — confirmed by reading the output, not assumed.

**Chunk 2 — the typed client and its failure union**
`openapi-fetch` client, base URL from config, the `ok | notFound | offline | server | malformed`
mapping, 10 s timeout. No screens.
*Acceptance*: unit tests for each branch including a malformed body and a timeout; no hand-written
request or response type anywhere (grep for the schema names proves it).

**Chunk 3 — home and results**
Screens 1 and 2 with all four states.
*Acceptance*: each state test-covered; a search for nonsense shows the empty state with no
"add property" offer; result rows are accessible buttons; nothing renders `score`.

**Chunk 4 — property detail header**
Screen 3's header, parallel fetch, the data-sufficiency notice, the 404 copy.
*Acceptance*: header renders from a fixture; `DRAFT` shows its notice; a 404 shows the "no longer
listed" copy; aliases de-duplicate against the title.

**Chunk 5 — the review feed**
Cursor paging, feed-level error that preserves loaded pages, empty feed, the review card.
*Acceptance*: paging test walks two pages and stops on a null cursor; a failed second page keeps the
first; `authorAccountId` absent from the tree; the card survives 200% text scaling on one device.

**Chunk 6 — optional, only if chunks 1–5 land clean**
Review detail screen and device-local recent searches.

## Acceptance criteria (the slice)

- [ ] A person with no account can search, open a property, and read its reviews on iOS and Android.
- [ ] No screen requires, prompts for, or implies an account.
- [ ] No request carries an `Authorization` header — asserted by a test, because "anonymous" silently
      becoming "authenticated later" is exactly the kind of thing that happens by accident.
- [ ] Every networked screen has loading, empty, error and loaded states, each test-covered.
- [ ] An error is always recoverable without restarting the app.
- [ ] `en` and `ru` complete, with correct Russian plurals; a missing key fails the typecheck.
- [ ] No hand-written API request/response type; all generated from `docs/api/openapi.json`.
- [ ] No internal identifier (`authorAccountId`, `score`, `version`) is rendered.
- [ ] Touch targets ≥ 44 pt; roles and labels on all controls; readable at 200% text size.
- [ ] No review count is displayed that the client did not count.
- [ ] `apps/api` is unchanged by this plan.
- [ ] A mobile-only pull request actually runs the frontend check, confirmed on a real pull request.

## Non-goals

Authentication and login · review submission or editing · helpful voting · verification flows ·
reports and appeals · evidence · maps, geocoding, and location-based search · notifications ·
representative features · saved/compare · photos · backend redesign · new infrastructure · new CI
jobs or emulator infrastructure · device e2e automation · machine translation of review content · a
tab bar for features that do not exist.

The two-line path addition in chunk 1 is not an exception to "no CI changes" — it is the minimum
needed for the existing gate to keep telling the truth once a second frontend app exists.

## Decisions needed before or during design

Each is a real fork, not a rhetorical question. The first four come from reading the contract.

**1. Search results have no address.** `PropertySearchHitResponse` carries only `canonicalName`,
`propertyId`, `score`, `distanceMeters`. Two buildings with the same or similar name are
indistinguishable in a result list, which is common in a city.
*Options*: (a) name-only rows, disambiguated by opening one — ship this now; (b) the client fetches
`/api/properties/{id}` per hit — N+1 on mobile data, rejected; (c) add `address` to the search hit —
a small additive backend change, and the one I would ask for after this slice proves the need with
real queries.
*Recommendation*: (a) now, (c) as a follow-up with evidence. **Needs: human.**

**2. No review count or aggregate rating exists anywhere in the API.** `PropertyResponse` has no
counts; `DESIGN_HANDOFF.md`'s property card asks for review count, verified-review count, a summary
with data-sufficiency, and a low-data warning. The card as specified cannot be built.
*Options*: (a) this slice shows only what it counted ("first 20 reviews") and no aggregates; (b) add
`reviewCount` and `verifiedReviewCount` to `PropertyResponse`.
*Recommendation*: (a) for the slice, and (b) is the strongest candidate for the next backend change
because the trust model — "warn when there is little data" — depends on a count.
**Needs: Dasha (card without aggregates) + human (backend follow-up).**

**3. The public review feed returns `authorAccountId`, and no pseudonym.** Any anonymous client gets a
stable identifier that can be correlated across properties to assemble one person's review history;
meanwhile the pseudonym that `PRD_MVP.md` §5.7 says is the public identity is not exposed at all, so
there is nothing to display as an author.
*Options*: (a) the app shows no author identity and attributes by relationship and verification
instead ("Current resident · Relationship verified") — privacy-preserving, and arguably the better
product; (b) the API exposes `authorPseudonym` and stops sending `authorAccountId`.
*Recommendation*: (a) for the slice regardless of (b). Whether the API should keep handing out
`authorAccountId` anonymously is a **privacy question that deserves an answer independently of this
plan**. **Needs: human.**

**4. Search cannot page.** It takes `limit` but returns no cursor and no total, so there is no second
page and no "12 results".
*Recommendation*: `limit=20`, and say "showing the first 20" when exactly 20 return. Add a cursor
only if real use shows people hitting the ceiling. **Needs: nobody — recorded so the wording is not
mistaken for a bug.**

**5. Visual and tone decisions that are Dasha's, not mine.** Verification badge form and its
explanatory sheet; how a critical review is presented so it reads as information rather than
sensation (`DESIGN_HANDOFF.md` "Product tone"); category chip vocabulary and iconography; the
data-sufficiency notice's visual weight — it must be noticeable without making every new building
look disreputable; empty-state illustration or none; palette meeting 4.5:1 body contrast; whether
`ka` ships as a third UI locale in this slice or the next. **Needs: Dasha.**

**6. Product question.** The app shows `DRAFT` properties, which are resident-added and unreviewed.
Is that the intended first impression for a stranger in Batumi, or should the mobile client filter to
`ACTIVE` until a property has some corroboration? Filtering is a client decision that needs no
backend change, but it changes what the app appears to know. **Needs: human.**

## Risks

- **`Intl` in Hermes** is the highest-probability technical surprise and the one with the widest
  blast radius; chunk 1 exists partly to find out early.
- **Expo adds native postinstall scripts**, and `pnpm-workspace.yaml` names allowed builds
  explicitly. Chunk 1 will need additions there, and each should be a reviewed line rather than a
  blanket allow.
- **The generated client is Next-shaped in `apps/web`**; the generator is reusable but the fetch
  layer is not, and copying `serverApi()` wholesale would drag in cookie and server-component
  assumptions that do not exist on a device.
- **Mobile enters the shared `pnpm -r` gate the moment it joins the workspace.** A slow or flaky
  React Native Jest setup then slows or breaks `apps/web`'s pull requests too. Keep mobile's tests
  node-only (RNTL, no native modules under test) so they stay in the seconds range.
- **Reading the seeded data honestly**: the fixtures are Georgian text with an `en`/`ru` UI. If that
  looks broken during review, it is the product's real shape, not a bug in the app.

## Progress log

- 2026-09-22: plan written. Contract inspected — the slice needs no backend change; four contract
  gaps and two product questions recorded above. Also found that the root `pnpm -r` scripts pull any
  new workspace member into the L2 gate automatically, while `frontend-check.yml`'s relevance regex
  would not fire for a mobile-only change — folded into chunk 1. Not implemented.

## Final outcome

Pending.

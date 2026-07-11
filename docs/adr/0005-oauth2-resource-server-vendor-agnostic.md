# ADR-0005: OAuth2 Resource Server, vendor-agnostic

Status: Accepted
Date: 2026-07-09
Deciders: Founders
Related: `docs/plans/002-identity-module.md`, P-008 (auth provider decision)

## Context

P-008 (accepted, `docs/DECISION_LOG.md`) commits to a managed OIDC/IdP provider but explicitly defers the specific vendor to a follow-up ADR — picking one means creating a real external account (billing, dashboard access), which is a founder action outside engineering scope. The identity module needed to be built now, so the backend needed an authentication design that doesn't hard-code a vendor.

## Decision drivers

- Don't block backend work on an external vendor sign-up.
- `docs/SECURITY_PRIVACY.md` §4: "modern OAuth/OIDC provider; no custom password storage unless strongly justified."
- `docs/API_GUIDELINES.md`: never trust client-supplied role/verification/moderation state.

## Considered options

### Option A — OAuth2 Resource Server, vendor-agnostic (chosen)

Spring Security's `spring-boot-starter-oauth2-resource-server` validates JWTs issued by any standards-compliant OIDC provider via a configurable `jwk-set-uri`. Clients (mobile/web) authenticate directly against the IdP; our backend never sees a password, only validates bearer tokens.

### Option B — Pick a vendor now and integrate directly

Would unblock nothing engineering-wise that Option A doesn't already unblock, while forcing the vendor decision ahead of when the founders are ready to create/fund an external account.

### Option C — Custom username/password auth

Rejected outright — directly contradicts SECURITY_PRIVACY.md's "no custom password storage unless strongly justified," and there is no justification here.

## Decision

Use `spring-boot-starter-oauth2-resource-server` with JWT validation via `spring.security.oauth2.resourceserver.jwt.jwk-set-uri`, configured from an environment variable (`OIDC_JWK_SET_URI`) with a non-resolvable placeholder default. The identity module's `IdentityJwtAuthenticationConverter` resolves the authenticated account and always derives the authorization role from our own `identity.account.role` column — never from any claim in the JWT itself — so this satisfies API_GUIDELINES' "no trust in client-supplied role" regardless of which vendor is eventually chosen.

**`jwk-set-uri`, not `issuer-uri`, is a deliberate choice, not just a placeholder detail**: `issuer-uri` triggers eager OIDC discovery (a blocking HTTP call to `{issuer}/.well-known/openid-configuration`) at `JwtDecoder` bean creation time. With no real vendor configured yet, that would hang or fail Spring context startup for every test, not just security tests. `jwk-set-uri` builds a decoder that only fetches lazily on an actual `decode()` call — tests use Spring Security Test's mock-JWT support (`SecurityMockMvcRequestPostProcessors.jwt(...)`), which populates the security context directly and never invokes the real decoder, so the placeholder URI is never dereferenced. Verified empirically as part of `docs/plans/002-identity-module.md` Chunk 5.

## Consequences

### Positive

- No engineering work is blocked on external vendor sign-up.
- Switching vendors later (once one is chosen) is a config-only change (`OIDC_JWK_SET_URI`), not a code change.
- Role-spoofing via a crafted JWT claim is structurally prevented, independent of vendor trustworthiness.

### Negative / trade-offs

- MFA/step-up auth for admins (SECURITY_PRIVACY.md §4) cannot be implemented until a vendor is chosen, since it depends on that vendor's `acr`/`amr` claim support.
- No real end-to-end manual testing against a live IdP is possible until a vendor exists — automated tests rely entirely on mock JWTs.
- First-ADMIN bootstrapping has no self-service path (by design — a self-service role-escalation endpoint would defeat the "never trust client role" guarantee) and requires a manual database update once a vendor and real users exist.

### Follow-up

- Founders pick and sign up for an actual IdP vendor (Auth0/Cognito/Clerk/Supabase Auth/etc. — see prior discussion in the scaffold-phase decision log context) and set `OIDC_JWK_SET_URI` accordingly. Track as a `HUMAN_ACTION_REQUIRED`-style item, not an engineering task.
- Once a vendor is live, add manual smoke-test steps against real tokens to `apps/api/README.md`.

## Revisit when

- A vendor is actually chosen (config change, plus this ADR should be updated to record which one and why).
- MFA/step-up auth is required for admin actions and the chosen vendor's claim shape is known.

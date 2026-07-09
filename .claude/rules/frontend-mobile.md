---
paths:
  - "apps/mobile/**/*.{ts,tsx,js,jsx,json}"
---

# Mobile rules

- Use generated OpenAPI client/types instead of duplicating contracts.
- Handle loading, empty, error, offline and permission-denied states.
- Preserve original review language when showing translation.
- Verification and moderation copy must match product definitions exactly.
- Do not expose raw storage keys, private URLs or internal risk scores.
- Accessibility labels, touch targets and dynamic text are required for reusable components.
- Keep secrets and privileged API logic out of the client.

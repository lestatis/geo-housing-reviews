---
paths:
  - "apps/admin/**/*.{ts,tsx,js,jsx,json}"
---

# Admin application rules

- Admin UI is not a security boundary; backend authorization is mandatory.
- Mask sensitive evidence by default and require explicit reveal with audit where implemented.
- Destructive decisions require confirmation, reason and current-version check.
- Never place raw evidence in browser analytics or error reporting.
- Support keyboard workflows without sacrificing confirmation for high-risk actions.

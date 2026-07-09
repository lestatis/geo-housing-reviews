#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

python3 scripts/validate_repo_governance.py

if [[ -d apps/api && -x apps/api/gradlew ]]; then
  (cd apps/api && ./gradlew check)
elif [[ -x ./gradlew ]]; then
  ./gradlew check
else
  echo "INFO: backend scaffold not present; Gradle checks skipped."
fi

if [[ -f package.json ]]; then
  if command -v corepack >/dev/null 2>&1; then
    corepack pnpm lint
    corepack pnpm test
    corepack pnpm typecheck
  else
    echo "ERROR: package.json exists but corepack is unavailable." >&2
    exit 1
  fi
else
  echo "INFO: frontend scaffold not present; pnpm checks skipped."
fi

echo "Repository checks passed."

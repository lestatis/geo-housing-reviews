#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

usage() {
  cat <<'EOF'
Usage:
  ./scripts/check-scoped.sh governance
  ./scripts/check-scoped.sh module <module-name>
  ./scripts/check-scoped.sh app-test <Gradle --tests pattern>
  ./scripts/check-scoped.sh frontend

Runs L1 worker-handoff checks. These intentionally skip mutation testing and never replace the L2
CI merge gate. Use module names such as moderation or identity, without the :modules: prefix.
EOF
}

if [[ "${1:-}" == "--help" || "${1:-}" == "-h" ]]; then
  usage
  exit 0
fi

case "${1:-}" in
  governance)
    [[ $# -eq 1 ]] || { usage >&2; exit 2; }
    python3 scripts/validate_repo_governance.py
    ;;
  module)
    [[ $# -eq 2 && "$2" =~ ^[a-z0-9-]+$ ]] || { usage >&2; exit 2; }
    (cd apps/api && ./gradlew ":modules:$2:check" -PskipMutation)
    ;;
  app-test)
    [[ $# -eq 2 && -n "$2" ]] || { usage >&2; exit 2; }
    (cd apps/api && ./gradlew :app:test --tests "$2" -PskipMutation)
    ;;
  frontend)
    [[ $# -eq 1 ]] || { usage >&2; exit 2; }
    if ! command -v corepack >/dev/null 2>&1 && [[ -s "${NVM_DIR:-$HOME/.nvm}/nvm.sh" ]]; then
      # shellcheck disable=SC1091
      . "${NVM_DIR:-$HOME/.nvm}/nvm.sh" >/dev/null 2>&1 || true
    fi
    command -v corepack >/dev/null 2>&1 || {
      echo "ERROR: corepack is required for frontend checks." >&2
      exit 1
    }
    corepack pnpm lint
    corepack pnpm test
    corepack pnpm typecheck
    ;;
  *)
    usage >&2
    exit 2
    ;;
esac

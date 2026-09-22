#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib.sh
source "$SCRIPT_DIR/lib.sh"

usage() {
  cat <<'EOF'
Usage: scripts/ai/claude-reviewer.sh [--result .ai/path.json] <base-ref> <plan-path> <worker-result>

Starts a fresh, read-only Claude Code review session. It does not write a fix, approve a pull
request, or merge. The worker result must already satisfy the worker result contract.
EOF
}

result_path=".ai/review-result.json"
if [[ "${1:-}" == "--help" || "${1:-}" == "-h" ]]; then
  usage
  exit 0
elif [[ "${1:-}" == "--result" ]]; then
  [[ $# -ge 2 ]] || ai_die "--result requires a path"
  result_path="$2"
  shift 2
fi
[[ $# -eq 3 ]] || { usage >&2; exit 2; }
base_ref="$1"
plan_path="$2"
worker_result="$3"

ai_require_repository
ai_validate_plan_path "$plan_path"
ai_validate_result_path "$result_path"
ai_validate_result_path "$worker_result"
git -C "$AI_ROOT" rev-parse --verify --quiet "${base_ref}^{commit}" >/dev/null \
  || ai_die "base ref is not a commit: $base_ref"
python3 "$SCRIPT_DIR/validate_result.py" worker "$AI_ROOT/$worker_result"

claude_bin="${AI_CLAUDE_BIN:-claude}"
ai_require_command "$claude_bin"
ai_require_command timeout
timeout_seconds="$(ai_timeout_seconds)"
schema_path="$SCRIPT_DIR/schemas/review-result.schema.json"
schema_json="$(tr -d '\n' < "$schema_path")"
absolute_result="$AI_ROOT/$result_path"
raw_result="${absolute_result}.raw.json"
mkdir -p "$(dirname "$absolute_result")"
initial_branch="$(git -C "$AI_ROOT" branch --show-current)"
initial_head="$(git -C "$AI_ROOT" rev-parse HEAD)"
initial_status="$(git -C "$AI_ROOT" status --porcelain)"

claude_args=(
  -p
  --no-session-persistence
  --output-format json
  --json-schema "$schema_json"
  --agent lead-reviewer
  --tools "Read,Grep,Glob,Bash"
  --disallowed-tools "Edit,Write,NotebookEdit"
  --permission-mode plan
)
if [[ -n "${AI_CLAUDE_REVIEW_MODEL:-}" ]]; then
  claude_args+=(--model "$AI_CLAUDE_REVIEW_MODEL")
fi

review_prompt=$(cat <<EOF
Review task for the current branch against \`${base_ref}\` in a fresh independent context.

Read AGENTS.md, \`${plan_path}\`, \`${worker_result}\`, and only the relevant accepted
documentation. Inspect the complete diff with \`git diff ${base_ref}...HEAD\` and the affected
code/tests. Treat the worker result only as claimed evidence; verify it yourself.

Do not edit, create files, commit, push, open a pull request, approve, or merge. Review only. Your
JSON result must use FIXES_REQUIRED for actionable findings, READY_TO_MERGE only when there are no
findings, and a stopping decision when review cannot be completed. This process still requires a
human merge decision.
EOF
)

timeout --foreground "${timeout_seconds}s" "$claude_bin" "${claude_args[@]}" "$review_prompt" \
  > "$raw_result"
[[ "$(git -C "$AI_ROOT" branch --show-current)" == "$initial_branch" \
  && "$(git -C "$AI_ROOT" rev-parse HEAD)" == "$initial_head" \
  && "$(git -C "$AI_ROOT" status --porcelain)" == "$initial_status" ]] \
  || ai_die "reviewer changed Git state; stop and inspect Git before continuing"
python3 "$SCRIPT_DIR/extract_claude_result.py" review "$raw_result" "$absolute_result"
printf 'Review result: %s\n' "$result_path"

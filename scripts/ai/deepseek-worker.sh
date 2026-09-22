#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib.sh
source "$SCRIPT_DIR/lib.sh"

usage() {
  cat <<'EOF'
Usage: scripts/ai/deepseek-worker.sh [--result .ai/path.json] [--review-findings .ai/review.json] \
  <plan-path> <chunk-id>

Runs one bounded implementation or fix packet through the explicitly configured DeepSeek Codex
model. AI_DEEPSEEK_MODEL is required. The current branch must already be a non-main feature branch.
EOF
}

result_path=".ai/worker-result.json"
review_findings=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --result)
      [[ $# -ge 2 ]] || ai_die "--result requires a path"
      result_path="$2"
      shift 2
      ;;
    --review-findings)
      [[ $# -ge 2 ]] || ai_die "--review-findings requires a path"
      review_findings="$2"
      shift 2
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    --*)
      ai_die "unknown option: $1"
      ;;
    *)
      break
      ;;
  esac
done

[[ $# -eq 2 ]] || { usage >&2; exit 2; }
plan_path="$1"
chunk_id="$2"

ai_require_repository
ai_require_feature_branch
ai_validate_plan_path "$plan_path"
ai_validate_result_path "$result_path"
[[ "$chunk_id" =~ ^[A-Za-z0-9][A-Za-z0-9._-]*$ ]] || ai_die "chunk id contains unsupported characters"
[[ -n "${AI_DEEPSEEK_MODEL:-}" ]] || ai_die "AI_DEEPSEEK_MODEL must name the configured DeepSeek model"
if [[ -n "$review_findings" ]]; then
  ai_validate_result_path "$review_findings"
  python3 "$SCRIPT_DIR/validate_result.py" review "$AI_ROOT/$review_findings"
fi

codex_bin="${AI_CODEX_BIN:-codex}"
ai_require_command "$codex_bin"
ai_require_command timeout
timeout_seconds="$(ai_timeout_seconds)"
schema="$SCRIPT_DIR/schemas/worker-result.schema.json"
absolute_result="$AI_ROOT/$result_path"
mkdir -p "$(dirname "$absolute_result")"
initial_branch="$(git -C "$AI_ROOT" branch --show-current)"
initial_head="$(git -C "$AI_ROOT" rev-parse HEAD)"

{
  sed -n '1,240p' "$AI_ROOT/prompts/DEEPSEEK_IMPLEMENT.md"
  printf '\n## Assigned packet\n\n'
  printf 'Plan: `%s`\nChunk: `%s`\nBranch: `%s`\n\n' \
    "$plan_path" "$chunk_id" "$(git -C "$AI_ROOT" branch --show-current)"
  printf '%s\n' 'Implement exactly this chunk. Preserve all existing work on the branch. Update the active plan and handoff when the task is non-trivial. Do not create a branch, commit, push, open a pull request, merge, access credentials, or use web tools.'
  printf '%s\n' 'Your final response must satisfy the supplied JSON Schema. `READY_FOR_LEAD_REVIEW` requires actual L1 evidence in `tests`; otherwise use a stopping status and explain it in `summary`.'
  if [[ -n "$review_findings" ]]; then
    printf '\n## Independent review findings to assess\n\n'
    sed -n '1,260p' "$AI_ROOT/$review_findings"
    printf '\n%s\n' 'Assess every finding against the current diff. Implement only valid corrections, add focused regression tests where appropriate, and record rejected or unresolved findings in `risks`.'
  fi
} | timeout --foreground "${timeout_seconds}s" "$codex_bin" exec \
  --model "$AI_DEEPSEEK_MODEL" \
  --sandbox workspace-write \
  --output-schema "$schema" \
  -o "$absolute_result" \
  -

[[ "$(git -C "$AI_ROOT" branch --show-current)" == "$initial_branch" \
  && "$(git -C "$AI_ROOT" rev-parse HEAD)" == "$initial_head" ]] \
  || ai_die "worker changed the branch or HEAD; stop and inspect Git before continuing"
python3 "$SCRIPT_DIR/validate_result.py" worker "$absolute_result"
printf 'Worker result: %s\n' "$result_path"

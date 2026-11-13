#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib.sh
source "$SCRIPT_DIR/lib.sh"

usage() {
  cat <<'EOF'
Usage: scripts/ai/deepseek-worker.sh [--result .ai/path.json] [--review-findings .ai/review.json] \
  [--runner-l1 .ai/l1-commands.txt] <plan-path> <chunk-id>

Runs one bounded implementation or fix packet through the explicitly configured DeepSeek Codex
profile and model. AI_CODEX_PROFILE and AI_DEEPSEEK_MODEL are required. The current branch must
already be a non-main feature branch. With --runner-l1 the worker is told that the loop runner runs
the listed L1 commands outside the sandbox, and a ready result needs no passed test of its own.
EOF
}

result_path=".ai/worker-result.json"
review_findings=""
runner_l1=""
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
    --runner-l1)
      [[ $# -ge 2 ]] || ai_die "--runner-l1 requires a path"
      runner_l1="$2"
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
[[ -n "${AI_CODEX_PROFILE:-}" ]] || ai_die "AI_CODEX_PROFILE must name the configured DeepSeek profile"
[[ "$AI_CODEX_PROFILE" =~ ^[A-Za-z0-9][A-Za-z0-9_-]*$ ]] || ai_die "AI_CODEX_PROFILE contains unsupported characters"
[[ -n "${AI_DEEPSEEK_MODEL:-}" ]] || ai_die "AI_DEEPSEEK_MODEL must name the configured DeepSeek model"
if [[ -n "$runner_l1" ]]; then
  ai_validate_result_path "$runner_l1"
  [[ -f "$AI_ROOT/$runner_l1" ]] || ai_die "runner L1 command file does not exist: $runner_l1"
fi
if [[ -n "$review_findings" ]]; then
  ai_validate_result_path "$review_findings"
  python3 "$SCRIPT_DIR/validate_result.py" review "$AI_ROOT/$review_findings"
fi

codex_bin="${AI_CODEX_BIN:-codex}"
ai_require_command "$codex_bin"
ai_require_command timeout
cd "$AI_ROOT"
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
  if [[ -n "$runner_l1" ]]; then
    printf '\n## L1 is run by the loop runner\n\n'
    printf '%s\n\n' 'Your sandbox has no network or Docker, so Gradle and Testcontainers cannot run in it. Do not try to work around that. After you finish, the runner runs these L1 checks itself, outside the sandbox, from the repository root:'
    sed 's/^/    /' "$AI_ROOT/$runner_l1"
    printf '\n%s\n' 'Run whatever narrower checks you can (compilation of single files, formatters, linters that need no network). Record the L1 commands above in `tests` as `skipped`. Return `READY_FOR_LEAD_REVIEW` when every in-scope acceptance criterion is implemented; a failure comes back to you as a fix packet. Do not change build scripts, `scripts/`, `.claude/`, `.github/` or `prompts/`: the runner refuses to run L1 after such a change and stops for the lead.'
    printf '%s\n' 'Your final response must satisfy the supplied JSON Schema.'
  else
    printf '%s\n' 'Your final response must satisfy the supplied JSON Schema. `READY_FOR_LEAD_REVIEW` requires actual L1 evidence in `tests`; otherwise use a stopping status and explain it in `summary`.'
  fi
  if [[ -n "$review_findings" ]]; then
    printf '\n## Findings to assess (independent review or runner L1)\n\n'
    sed -n '1,260p' "$AI_ROOT/$review_findings"
    printf '\n%s\n' 'This fix packet contains only findings with `blocks_merge: true`. Assess each finding against the current diff. Implement only valid corrections, add focused regression tests where appropriate, and record rejected or unresolved findings in `risks`. Non-blocking review notes remain for the human and are not part of this fix packet.'
  fi
} | timeout --foreground "${timeout_seconds}s" "$codex_bin" exec \
  --profile "$AI_CODEX_PROFILE" \
  --config 'model_provider="deepseek"' \
  --model "$AI_DEEPSEEK_MODEL" \
  --sandbox workspace-write \
  --output-schema "$schema" \
  -o "$absolute_result" \
  -

[[ "$(git -C "$AI_ROOT" branch --show-current)" == "$initial_branch" \
  && "$(git -C "$AI_ROOT" rev-parse HEAD)" == "$initial_head" ]] \
  || ai_die "worker changed the branch or HEAD; stop and inspect Git before continuing"
validate_args=(worker "$absolute_result")
[[ -n "$runner_l1" ]] && validate_args+=(--runner-l1)
python3 "$SCRIPT_DIR/validate_result.py" "${validate_args[@]}"
printf 'Worker result: %s\n' "$result_path"

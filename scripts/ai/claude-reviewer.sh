#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib.sh
source "$SCRIPT_DIR/lib.sh"

usage() {
  cat <<'EOF'
Usage: scripts/ai/claude-reviewer.sh [--result .ai/path.json] [--l1-result .ai/l1.json] \
  <base-ref> <plan-path> <worker-result>

Starts a fresh, read-only Claude Code review session. It does not write a fix, approve a pull
request, or merge. The worker result must already satisfy the worker result contract.
EOF
}

result_path=".ai/review-result.json"
l1_result=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --help|-h)
      usage
      exit 0
      ;;
    --result)
      [[ $# -ge 2 ]] || ai_die "--result requires a path"
      result_path="$2"
      shift 2
      ;;
    --l1-result)
      [[ $# -ge 2 ]] || ai_die "--l1-result requires a path"
      l1_result="$2"
      shift 2
      ;;
    --*)
      ai_die "unknown option: $1"
      ;;
    *)
      break
      ;;
  esac
done
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
worker_validate_args=(worker "$AI_ROOT/$worker_result")
if [[ -n "$l1_result" ]]; then
  ai_validate_result_path "$l1_result"
  [[ -f "$AI_ROOT/$l1_result" ]] || ai_die "L1 result does not exist: $l1_result"
  worker_validate_args+=(--runner-l1)
fi
python3 "$SCRIPT_DIR/validate_result.py" "${worker_validate_args[@]}"

claude_bin="${AI_CLAUDE_BIN:-claude}"
ai_require_command "$claude_bin"
ai_require_command timeout
[[ -n "${AI_CLAUDE_REVIEW_MODEL:-}" ]] || ai_die "AI_CLAUDE_REVIEW_MODEL must name the explicit reviewer model"
cd "$AI_ROOT"
timeout_seconds="$(ai_timeout_seconds)"
schema_path="$SCRIPT_DIR/schemas/review-result.schema.json"
# Claude Code cannot resolve the draft 2020-12 meta-schema reference and rejects the whole schema,
# so the "$schema" key is dropped from the argument; the file keeps it for other validators.
schema_json="$(python3 -c 'import json, sys; schema = json.load(open(sys.argv[1], encoding="utf-8")); schema.pop("$schema", None); print(json.dumps(schema, separators=(",", ":")))' "$schema_path")"
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
  --model "$AI_CLAUDE_REVIEW_MODEL"
)

review_prompt=$(cat <<EOF
Review task for the current branch against \`${base_ref}\` in a fresh independent context.

Read AGENTS.md, \`${plan_path}\`, \`${worker_result}\`, and only the relevant accepted
documentation. Inspect the complete current working-tree change with \`git diff --no-ext-diff ${base_ref}\`,
then list untracked files with \`git ls-files --others --exclude-standard\` and inspect every relevant
untracked code, test and documentation file. Start with \`git status --short\`. The worker result is
only claimed evidence; verify it yourself.

Do not edit, create files, commit, push, open a pull request, approve, or merge. Review only. Your
JSON result must use FIXES_REQUIRED only when at least one finding has \`blocks_merge: true\`.
READY_TO_MERGE may retain non-blocking findings, all with \`blocks_merge: false\`; they are reported
to the human but must not start another worker cycle. Use a stopping decision when review cannot be
completed. This process still requires a human merge decision.
EOF
)
if [[ -n "$l1_result" ]]; then
  review_prompt+="

The loop runner, not the worker, ran the chunk's L1 checks outside the worker sandbox and they
passed; its evidence and per-command logs are in \`${l1_result}\`. Confirm the commands there match
the plan's L1 for this chunk."
fi

timeout --foreground "${timeout_seconds}s" "$claude_bin" "${claude_args[@]}" "$review_prompt" \
  > "$raw_result"
[[ "$(git -C "$AI_ROOT" branch --show-current)" == "$initial_branch" \
  && "$(git -C "$AI_ROOT" rev-parse HEAD)" == "$initial_head" \
  && "$(git -C "$AI_ROOT" status --porcelain)" == "$initial_status" ]] \
  || ai_die "reviewer changed Git state; stop and inspect Git before continuing"
python3 "$SCRIPT_DIR/extract_claude_result.py" review "$raw_result" "$absolute_result"
printf 'Review result: %s\n' "$result_path"

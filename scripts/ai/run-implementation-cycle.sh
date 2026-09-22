#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib.sh
source "$SCRIPT_DIR/lib.sh"

usage() {
  cat <<'EOF'
Usage: scripts/ai/run-implementation-cycle.sh [--max-cycles 1|2] <base-ref> <plan-path> <chunk-id>

Runs one DeepSeek worker packet and, only after READY_FOR_LEAD_REVIEW, a fresh read-only Claude
review. FIXES_REQUIRED findings are returned to the worker. The loop stops after at most two review
rounds and never creates branches, commits, pushes, opens PRs, or merges.
EOF
}

max_cycles=2
if [[ "${1:-}" == "--help" || "${1:-}" == "-h" ]]; then
  usage
  exit 0
elif [[ "${1:-}" == "--max-cycles" ]]; then
  [[ $# -ge 2 ]] || ai_die "--max-cycles requires 1 or 2"
  max_cycles="$2"
  shift 2
fi
[[ "$max_cycles" =~ ^[12]$ ]] || ai_die "--max-cycles must be 1 or 2"
[[ $# -eq 3 ]] || { usage >&2; exit 2; }
base_ref="$1"
plan_path="$2"
chunk_id="$3"

ai_require_repository
ai_require_feature_branch
ai_validate_plan_path "$plan_path"
git -C "$AI_ROOT" rev-parse --verify --quiet "${base_ref}^{commit}" >/dev/null \
  || ai_die "base ref is not a commit: $base_ref"

mkdir -p "$AI_ROOT/.ai/runs"
run_dir="$(mktemp -d "$AI_ROOT/.ai/runs/implementation-loop.XXXXXX")"
relative_run_dir="${run_dir#"$AI_ROOT/"}"
worker_result="$relative_run_dir/worker-0.json"
review_findings=""
review_round=0

read_json_field() {
  python3 - "$1" "$2" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as source:
    value = json.load(source)[sys.argv[2]]
print(value)
PY
}

while true; do
  worker_args=(--result "$worker_result")
  if [[ -n "$review_findings" ]]; then
    worker_args+=(--review-findings "$review_findings")
  fi
  "$SCRIPT_DIR/deepseek-worker.sh" "${worker_args[@]}" "$plan_path" "$chunk_id"

  worker_status="$(read_json_field "$AI_ROOT/$worker_result" status)"
  if [[ "$worker_status" != "READY_FOR_LEAD_REVIEW" ]]; then
    printf 'STOPPED: worker returned %s. Evidence: %s\n' "$worker_status" "$worker_result" >&2
    exit 4
  fi

  review_round=$((review_round + 1))
  review_result="$relative_run_dir/review-${review_round}.json"
  "$SCRIPT_DIR/claude-reviewer.sh" --result "$review_result" "$base_ref" "$plan_path" "$worker_result"
  decision="$(read_json_field "$AI_ROOT/$review_result" decision)"
  case "$decision" in
    READY_TO_MERGE)
      printf 'READY_FOR_HUMAN_MERGE\nWorker evidence: %s\nReview evidence: %s\n' \
        "$worker_result" "$review_result"
      exit 0
      ;;
    FIXES_REQUIRED)
      if [[ "$review_round" -ge "$max_cycles" ]]; then
        printf 'STOPPED: review/fix cycle cap (%s) reached. Findings: %s\n' \
          "$max_cycles" "$review_result" >&2
        exit 5
      fi
      review_findings="$review_result"
      worker_result="$relative_run_dir/worker-${review_round}.json"
      ;;
    *)
      printf 'STOPPED: reviewer returned %s. Evidence: %s\n' "$decision" "$review_result" >&2
      exit 4
      ;;
  esac
done

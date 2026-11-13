#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib.sh
source "$SCRIPT_DIR/lib.sh"

usage() {
  cat <<'EOF'
Usage: scripts/ai/run-implementation-cycle.sh [--max-cycles 1|2] <base-ref> <plan-path> <chunk-id>

Runs one DeepSeek worker packet and, only after READY_FOR_LEAD_REVIEW, a fresh read-only Claude
  review. If the chunk's plan section declares an ```ai-l1 block, the runner copies it at start and
  runs those commands itself, outside the worker sandbox, between the worker and the review; a
  failure goes back to the worker as a fix packet. Only merge-blocking FIXES_REQUIRED findings are
  returned to the worker. --max-cycles bounds worker runs (and so reviews) at 1 or 2. The loop never
  creates branches, commits, pushes, opens PRs, or merges.
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
review_findings=""
review_round=0
worker_runs=0

# Snapshot the chunk's L1 now: the worker may edit the plan, and must not choose what runs outside its
# sandbox.
l1_commands=""
set +e
python3 "$SCRIPT_DIR/extract-l1-checks.py" "$AI_ROOT/$plan_path" "$chunk_id" "$run_dir/l1-commands.txt"
extract_status=$?
set -e
case "$extract_status" in
  0)
    l1_commands="$relative_run_dir/l1-commands.txt"
    python3 "$SCRIPT_DIR/protected-fingerprint.py" "$AI_ROOT" > "$run_dir/protected-before.txt"
    printf 'Runner-owned L1 for %s (%s command(s)): %s\n' "$chunk_id" \
      "$(wc -l < "$run_dir/l1-commands.txt" | tr -d ' ')" "$l1_commands"
    ;;
  3)
    printf 'No ai-l1 block for %s; the worker must report its own L1 evidence.\n' "$chunk_id"
    ;;
  *)
    ai_die "cannot read the ai-l1 block for $chunk_id in $plan_path"
    ;;
esac

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
  worker_result="$relative_run_dir/worker-${worker_runs}.json"
  worker_args=(--result "$worker_result")
  if [[ -n "$review_findings" ]]; then
    worker_args+=(--review-findings "$review_findings")
  fi
  if [[ -n "$l1_commands" ]]; then
    worker_args+=(--runner-l1 "$l1_commands")
  fi
  "$SCRIPT_DIR/deepseek-worker.sh" "${worker_args[@]}" "$plan_path" "$chunk_id"
  worker_runs=$((worker_runs + 1))

  worker_status="$(read_json_field "$AI_ROOT/$worker_result" status)"
  if [[ "$worker_status" != "READY_FOR_LEAD_REVIEW" ]]; then
    printf 'STOPPED: worker returned %s. Evidence: %s\n' "$worker_status" "$worker_result" >&2
    exit 4
  fi

  reviewer_args=(--result "$relative_run_dir/review-$((review_round + 1)).json")
  if [[ -n "$l1_commands" ]]; then
    python3 "$SCRIPT_DIR/protected-fingerprint.py" "$AI_ROOT" > "$run_dir/protected-after-${worker_runs}.txt"
    if ! changed="$(diff "$run_dir/protected-before.txt" "$run_dir/protected-after-${worker_runs}.txt")"; then
      printf 'STOPPED: LEAD_DECISION_REQUIRED. The worker changed build, automation or agent files, which\n' >&2
      printf 'L1 would run outside the sandbox unreviewed. Review them, then run L1 by hand:\n%s\n' \
        "$(printf '%s\n' "$changed" | sed -n 's/^[<>] [0-9a-f]*  /  /p; s/^[<>] missing  /  /p' | sort -u)" >&2
      printf 'Evidence: %s\n' "$worker_result" >&2
      exit 4
    fi
    l1_findings="$relative_run_dir/l1-findings-${worker_runs}.json"
    set +e
    python3 "$SCRIPT_DIR/run-l1-checks.py" --root "$AI_ROOT" --commands "$AI_ROOT/$l1_commands" \
      --run-dir "$relative_run_dir" --round "$worker_runs" --findings "$l1_findings"
    l1_status=$?
    set -e
    case "$l1_status" in
      0)
        reviewer_args+=(--l1-result "$relative_run_dir/l1-${worker_runs}.json")
        ;;
      1)
        if [[ "$worker_runs" -ge "$max_cycles" ]]; then
          printf 'STOPPED: L1 failed and the cycle cap (%s) is reached. Evidence: %s\n' \
            "$max_cycles" "$relative_run_dir/l1-${worker_runs}.json" >&2
          exit 5
        fi
        review_findings="$l1_findings"
        continue
        ;;
      *)
        ai_die "the runner could not run L1; see $relative_run_dir"
        ;;
    esac
  fi

  review_round=$((review_round + 1))
  review_result="$relative_run_dir/review-${review_round}.json"
  "$SCRIPT_DIR/claude-reviewer.sh" "${reviewer_args[@]}" "$base_ref" "$plan_path" "$worker_result"
  decision="$(read_json_field "$AI_ROOT/$review_result" decision)"
  case "$decision" in
    READY_TO_MERGE)
      printf 'READY_FOR_HUMAN_MERGE\nWorker evidence: %s\nReview evidence: %s\n' \
        "$worker_result" "$review_result"
      exit 0
      ;;
    FIXES_REQUIRED)
      if [[ "$worker_runs" -ge "$max_cycles" ]]; then
        printf 'STOPPED: review/fix cycle cap (%s) reached. Findings: %s\n' \
          "$max_cycles" "$review_result" >&2
        exit 5
      fi
      review_findings="$relative_run_dir/blocking-findings-${review_round}.json"
      python3 "$SCRIPT_DIR/filter-blocking-findings.py" \
        "$AI_ROOT/$review_result" "$AI_ROOT/$review_findings"
      ;;
    *)
      printf 'STOPPED: reviewer returned %s. Evidence: %s\n' "$decision" "$review_result" >&2
      exit 4
      ;;
  esac
done

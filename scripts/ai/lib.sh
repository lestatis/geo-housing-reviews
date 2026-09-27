#!/usr/bin/env bash

set -euo pipefail

AI_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

ai_die() {
  echo "ERROR: $*" >&2
  exit 2
}

ai_require_command() {
  command -v "$1" >/dev/null 2>&1 || ai_die "required command is unavailable: $1"
}

ai_require_repository() {
  git -C "$AI_ROOT" rev-parse --is-inside-work-tree >/dev/null 2>&1 \
    || ai_die "scripts/ai must run inside a Git worktree"
}

ai_require_feature_branch() {
  local branch
  branch="$(git -C "$AI_ROOT" branch --show-current)"
  [[ -n "$branch" && "$branch" != "main" && "$branch" != "master" ]] \
    || ai_die "run the automation from an existing non-main feature branch"
}

ai_validate_plan_path() {
  local plan_path="$1"
  [[ "$plan_path" == docs/plans/*.md && "$plan_path" != *".."* && -f "$AI_ROOT/$plan_path" ]] \
    || ai_die "plan must be an existing repository-relative docs/plans/*.md file"
}

ai_validate_result_path() {
  local result_path="$1"
  [[ "$result_path" == .ai/* && "$result_path" != *".."* ]] \
    || ai_die "result path must be repository-relative and under .ai/"
}

ai_timeout_seconds() {
  local seconds="${AI_COMMAND_TIMEOUT_SECONDS:-1800}"
  [[ "$seconds" =~ ^[1-9][0-9]*$ && "$seconds" -le 7200 ]] \
    || ai_die "AI_COMMAND_TIMEOUT_SECONDS must be an integer from 1 through 7200"
  printf '%s' "$seconds"
}

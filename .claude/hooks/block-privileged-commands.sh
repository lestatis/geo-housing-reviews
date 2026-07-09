#!/usr/bin/env bash
set -euo pipefail

# Claude Code sends hook input as JSON on stdin.
INPUT="$(cat)"
COMMAND="$(printf '%s' "$INPUT" | python3 -c 'import json,sys; print(json.load(sys.stdin).get("tool_input", {}).get("command", ""))')"

# Block privilege escalation, system package managers, dangerous permission bypasses,
# and writes to common system directories. The AGENTS.md contract requires a human handoff.
PATTERN='(^|[;&|()[:space:]])(sudo|doas|pkexec)([[:space:]]|$)|(^|[;&|()[:space:]])su([[:space:]]+-|[[:space:]]+-c|[[:space:]]*$)|(^|[;&|()[:space:]])(apt|apt-get|dnf|yum|pacman|zypper|snap)([[:space:]]|$)|chmod[[:space:]]+(-R[[:space:]]+)?777|(^|[;&|()[:space:]])(tee|cp|mv|install|rm|mkdir|touch|ln|chmod|chown)[^;&|]*(/etc|/usr|/opt|/var/lib|/var/run)(/|[[:space:]]|$)|curl[^|]*\|[[:space:]]*(sudo[[:space:]]+)?(sh|bash)|wget[^|]*\|[[:space:]]*(sudo[[:space:]]+)?(sh|bash)'

if printf '%s' "$COMMAND" | grep -Eiq "$PATTERN"; then
  python3 - <<'PY'
import json
print(json.dumps({
  "hookSpecificOutput": {
    "hookEventName": "PreToolUse",
    "permissionDecision": "deny",
    "permissionDecisionReason": (
      "Privileged/system-modifying command blocked. Follow AGENTS.md: output "
      "HUMAN_ACTION_REQUIRED with the exact command, reason, expected result and "
      "verification command. Do not attempt a workaround or permission bypass."
    )
  }
}))
PY
  exit 0
fi

# No output means the tool call is allowed.
exit 0

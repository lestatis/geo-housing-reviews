#!/usr/bin/env python3
from __future__ import annotations

import json
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

# Directories this repository does not author. Scanning them says nothing about our governance: a
# dependency's tsconfig.json legitimately contains comments (so it is not JSON), and a JOSE library
# legitimately contains the words "BEGIN PRIVATE KEY". Reporting either as a finding would train the
# reader to ignore this script's output, which is the only thing it has.
IGNORED_DIRECTORIES = {".git", "node_modules", ".next", "build", ".gradle", ".venv", "dist"}


def is_ours(path: Path) -> bool:
    return not IGNORED_DIRECTORIES.intersection(path.relative_to(ROOT).parts)


REQUIRED = [
    "README.md",
    "AGENTS.md",
    "CLAUDE.md",
    "PLANS.md",
    "CONTRIBUTING.md",
    "SECURITY.md",
    "docs/PRD_MVP.md",
    "docs/ARCHITECTURE.md",
    "docs/DOMAIN_MODEL.md",
    "docs/TRUST_VERIFICATION.md",
    "docs/MODERATION.md",
    "docs/SECURITY_PRIVACY.md",
    "docs/AI_WORKFLOW.md",
    "docs/handoffs/README.md",
    "docs/handoffs/TEMPLATE.md",
    "prompts/CODEX_TAKEOVER.md",
    "prompts/CLAUDE_TAKEOVER.md",
    "prompts/RECOVER_INTERRUPTED_TASK.md",
    "prompts/INDEPENDENT_REVIEW.md",
    "prompts/DEEPSEEK_IMPLEMENT.md",
    ".claude/settings.json",
    ".claude/hooks/block-privileged-commands.sh",
    ".claude/skills/task-handoff/SKILL.md",
    ".agents/skills/task-handoff/SKILL.md",
]

errors: list[str] = []

for relative in REQUIRED:
    path = ROOT / relative
    if not path.is_file():
        errors.append(f"missing required file: {relative}")

for path in ROOT.rglob("*.json"):
    if not is_ours(path):
        continue
    try:
        json.loads(path.read_text(encoding="utf-8"))
    except Exception as exc:
        errors.append(f"invalid JSON {path.relative_to(ROOT)}: {exc}")

for path in [ROOT / "AGENTS.md", ROOT / "CLAUDE.md"]:
    if path.is_file() and path.stat().st_size > 32 * 1024:
        errors.append(f"instruction file exceeds 32 KiB: {path.relative_to(ROOT)}")

agents = (ROOT / "AGENTS.md").read_text(encoding="utf-8") if (ROOT / "AGENTS.md").is_file() else ""
for marker in ["HUMAN_ACTION_REQUIRED", "sudo", "Sources of truth", "Definition of done"]:
    if marker not in agents:
        errors.append(f"AGENTS.md missing marker: {marker}")

claude = (ROOT / "CLAUDE.md").read_text(encoding="utf-8") if (ROOT / "CLAUDE.md").is_file() else ""
if "@AGENTS.md" not in claude:
    errors.append("CLAUDE.md must import @AGENTS.md")

for platform in [".claude/skills", ".agents/skills"]:
    base = ROOT / platform
    if not base.is_dir():
        errors.append(f"missing skills directory: {platform}")
        continue
    for skill_file in base.glob("*/SKILL.md"):
        text = skill_file.read_text(encoding="utf-8")
        if not text.startswith("---\n") or "name:" not in text or "description:" not in text:
            errors.append(f"invalid skill frontmatter: {skill_file.relative_to(ROOT)}")

claude_skills = {p.parent.name for p in (ROOT / ".claude/skills").glob("*/SKILL.md")}
codex_skills = {p.parent.name for p in (ROOT / ".agents/skills").glob("*/SKILL.md")}
if claude_skills != codex_skills:
    errors.append("Claude and Codex shared skill names differ")
if "task-handoff" not in claude_skills or "task-handoff" not in codex_skills:
    errors.append("task-handoff skill must exist for both Claude and Codex")

hook = ROOT / ".claude/hooks/block-privileged-commands.sh"
if hook.is_file():
    result = subprocess.run(["bash", "-n", str(hook)], capture_output=True, text=True)
    if result.returncode != 0:
        errors.append(f"invalid hook shell syntax: {result.stderr.strip()}")

# Catch obvious secrets without pretending this replaces a real scanner.
secret_patterns = [
    re.compile(r"AKIA[0-9A-Z]{16}"),
    re.compile(r"-----BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY-----"),
]
for path in ROOT.rglob("*"):
    if not path.is_file() or not is_ours(path):
        continue
    if path.stat().st_size > 2_000_000:
        continue
    try:
        text = path.read_text(encoding="utf-8")
    except UnicodeDecodeError:
        continue
    for pattern in secret_patterns:
        if pattern.search(text):
            errors.append(f"possible secret in {path.relative_to(ROOT)}")

if errors:
    print("Governance validation failed:", file=sys.stderr)
    for error in errors:
        print(f"- {error}", file=sys.stderr)
    raise SystemExit(1)

print(
    f"Governance validation passed: {len(REQUIRED)} required files, "
    f"{len(claude_skills)} shared skills."
)

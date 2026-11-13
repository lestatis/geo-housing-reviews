#!/usr/bin/env python3
"""Copy a chunk's runner-owned L1 commands out of its plan section.

The chunk's section starts at the heading that begins with the chunk id and ends at the next heading
of the same or a higher level. Inside it, every fenced block opened with exactly ```ai-l1 contributes
its non-blank, non-comment lines, one command per line.

Exit codes: 0 commands written, 3 the chunk declares no ai-l1 block, 2 invalid input.
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

HEADING = re.compile(r"^(#{1,6})\s+(.*)$")
FENCE = re.compile(r"^(`{3,}|~{3,})(.*)$")


def chunk_section(lines: list[str], chunk_id: str) -> list[str]:
    start = level = None
    in_fence = False
    for index, line in enumerate(lines):
        if FENCE.match(line):
            in_fence = not in_fence
            continue
        heading = None if in_fence else HEADING.match(line)
        if not heading:
            continue
        depth = len(heading.group(1))
        if start is None:
            title = heading.group(2).strip()
            if title == chunk_id or re.match(re.escape(chunk_id) + r"(\s|$|[—:-])", title):
                start, level = index + 1, depth
        elif depth <= level:
            return lines[start:index]
    return [] if start is None else lines[start:]


def l1_commands(section: list[str]) -> list[str]:
    commands: list[str] = []
    fence: str | None = None
    collecting = False
    for line in section:
        match = FENCE.match(line.strip())
        if fence is None:
            if match:
                fence = match.group(1)
                collecting = match.group(2).strip() == "ai-l1"
            continue
        if match and match.group(1).startswith(fence[0]) and len(match.group(1)) >= len(fence) and not match.group(2).strip():
            fence, collecting = None, False
            continue
        if collecting:
            command = line.strip()
            if not command or command.startswith("#"):
                continue
            if command.endswith("\\"):
                raise ValueError(f"ai-l1 blocks take one command per line, without continuations: {command}")
            commands.append(command)
    if fence is not None:
        raise ValueError("unterminated fenced block in the chunk section")
    return commands


def main() -> int:
    if len(sys.argv) != 4:
        print("Usage: extract-l1-checks.py <plan.md> <chunk-id> <output.txt>", file=sys.stderr)
        return 2
    plan, chunk_id, output = Path(sys.argv[1]), sys.argv[2], Path(sys.argv[3])
    try:
        commands = l1_commands(chunk_section(plan.read_text(encoding="utf-8").splitlines(), chunk_id))
    except (OSError, ValueError) as error:
        print(f"Cannot read L1 checks: {error}", file=sys.stderr)
        return 2
    if not commands:
        return 3
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text("\n".join(commands) + "\n", encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

#!/usr/bin/env python3
"""Fingerprint files that decide how commands run, so the runner can refuse unreviewed changes.

Prints one `<sha256>  <path>` line per tracked or untracked, non-ignored protected file, sorted.
The runner compares the output taken at loop start with the output after the worker: L1 runs
outside the worker sandbox, and a changed build script or automation file would run unreviewed.
"""

from __future__ import annotations

import fnmatch
import hashlib
import subprocess
import sys
from pathlib import Path

PREFIXES = ("scripts/", ".claude/", ".github/", "prompts/", ".codex/")
ROOT_FILES = {"AGENTS.md", "CLAUDE.md"}
DIRECTORIES = {"buildSrc", "build-logic", "gradle"}
NAME_PATTERNS = (
    "*.gradle",
    "*.gradle.kts",
    "gradlew",
    "gradlew.bat",
    "gradle.properties",
    "package.json",
    "pnpm-lock.yaml",
    "pnpm-workspace.yaml",
    ".npmrc",
    "Makefile",
    "Dockerfile*",
    "docker-compose*",
)


def protected(path: str) -> bool:
    parts = path.split("/")
    return (
        path.startswith(PREFIXES)
        or path in ROOT_FILES
        or any(part in DIRECTORIES for part in parts[:-1])
        or any(fnmatch.fnmatch(parts[-1], pattern) for pattern in NAME_PATTERNS)
    )


def main() -> int:
    root = Path(sys.argv[1]) if len(sys.argv) > 1 else Path.cwd()
    listed = subprocess.run(
        ["git", "-C", str(root), "ls-files", "-z", "--cached", "--others", "--exclude-standard"],
        capture_output=True,
        check=True,
    ).stdout.decode("utf-8", errors="surrogateescape")
    for path in sorted({entry for entry in listed.split("\0") if entry and protected(entry)}):
        file = root / path
        digest = hashlib.sha256(file.read_bytes()).hexdigest() if file.is_file() else "missing"
        print(f"{digest}  {path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

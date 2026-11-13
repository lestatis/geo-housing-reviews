#!/usr/bin/env python3
"""Run a chunk's snapshotted L1 commands outside the worker sandbox.

Every command runs from the repository root in its own process group with a hard timeout; on
timeout the whole group is killed, so a Gradle client and its children cannot outlive the check.
All commands run even after a failure. Writes `l1-<round>.json` and one log per command into the run
directory and, on failure, a FIXES_REQUIRED review-shaped packet for the worker's fix path.

Exit codes: 0 all passed, 1 at least one failed or timed out, 2 invalid input.
"""

from __future__ import annotations

import argparse
import json
import os
import signal
import subprocess
import sys
import time
from pathlib import Path

TAIL_LINES = 60
TAIL_CHARACTERS = 6000
KILL_GRACE_SECONDS = 20


def timeout_seconds() -> int:
    raw = os.environ.get("AI_L1_TIMEOUT_SECONDS", "900")
    if not raw.isdigit() or not 1 <= int(raw) <= 7200:
        raise ValueError("AI_L1_TIMEOUT_SECONDS must be an integer from 1 through 7200")
    return int(raw)


def stop_group(process: subprocess.Popen[bytes]) -> None:
    for sig, wait in ((signal.SIGTERM, KILL_GRACE_SECONDS), (signal.SIGKILL, 5)):
        try:
            os.killpg(process.pid, sig)
        except ProcessLookupError:
            return
        try:
            process.wait(timeout=wait)
            return
        except subprocess.TimeoutExpired:
            continue


def run(command: str, root: Path, log: Path, limit: int) -> tuple[str, int | None, float]:
    started = time.monotonic()
    with log.open("wb") as output:
        process = subprocess.Popen(
            ["bash", "-c", command],
            cwd=root,
            stdin=subprocess.DEVNULL,
            stdout=output,
            stderr=subprocess.STDOUT,
            start_new_session=True,
        )
        try:
            code = process.wait(timeout=limit)
        except subprocess.TimeoutExpired:
            stop_group(process)
            output.write(f"\n[run-l1-checks] killed after the {limit}s limit\n".encode())
            return "timed_out", None, time.monotonic() - started
    return ("passed" if code == 0 else "failed"), code, time.monotonic() - started


def tail(log: Path) -> str:
    text = "\n".join(log.read_text(encoding="utf-8", errors="replace").splitlines()[-TAIL_LINES:])
    return text[-TAIL_CHARACTERS:]


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, required=True)
    parser.add_argument("--commands", type=Path, required=True)
    parser.add_argument("--run-dir", required=True, help="repository-relative run directory")
    parser.add_argument("--round", type=int, required=True)
    parser.add_argument("--findings", required=True, help="repository-relative fix packet path")
    args = parser.parse_args()
    try:
        limit = timeout_seconds()
        commands = [line for line in args.commands.read_text(encoding="utf-8").splitlines() if line.strip()]
    except (OSError, ValueError) as error:
        print(f"Cannot run L1 checks: {error}", file=sys.stderr)
        return 2

    results = []
    for number, command in enumerate(commands, start=1):
        log = f"{args.run_dir}/l1-{args.round}-{number}.log"
        print(f"L1 {args.round}.{number} START {command}", file=sys.stderr, flush=True)
        result, code, seconds = run(command, args.root, args.root / log, limit)
        print(f"L1 {args.round}.{number} {result.upper()} in {seconds:.0f}s (log: {log})", file=sys.stderr, flush=True)
        results.append({"command": command, "result": result, "exit_code": code, "seconds": round(seconds), "log": log})

    passed = all(entry["result"] == "passed" for entry in results)
    evidence = {"round": args.round, "timeout_seconds": limit, "passed": passed, "commands": results}
    (args.root / f"{args.run_dir}/l1-{args.round}.json").write_text(json.dumps(evidence, indent=2) + "\n", encoding="utf-8")
    if passed:
        return 0

    findings = []
    for entry in results:
        if entry["result"] == "passed":
            continue
        outcome = f"exit {entry['exit_code']}" if entry["result"] == "failed" else f"timed out after {limit}s"
        findings.append(
            {
                "severity": "blocker",
                "path": entry["log"],
                "line": None,
                "risk": f"L1 check failed ({outcome}): {entry['command']}",
                "correction": "Make this L1 check pass without weakening or skipping it. "
                f"The full log is {entry['log']}. Last lines:\n{tail(args.root / entry['log'])}",
                "blocks_merge": True,
            }
        )
    packet = {
        "decision": "FIXES_REQUIRED",
        "findings": findings,
        "risks": [],
        "human_action_required": False,
        "summary": f"The runner ran the chunk's L1 checks outside the sandbox; {len(findings)} did not pass.",
    }
    (args.root / args.findings).write_text(json.dumps(packet, indent=2) + "\n", encoding="utf-8")
    return 1


if __name__ == "__main__":
    raise SystemExit(main())

#!/usr/bin/env python3
"""Extract Claude Code's structured result from its print-mode JSON envelope."""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

from validate_result import ValidationError, validate


def extract(payload: object) -> object:
    if not isinstance(payload, dict):
        return payload
    structured = payload.get("structured_output")
    if isinstance(structured, dict):
        return structured
    result = payload.get("result")
    if isinstance(result, str):
        return json.loads(result)
    return payload


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("kind", choices=("worker", "review"))
    parser.add_argument("input", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    try:
        with args.input.open(encoding="utf-8") as source:
            result = validate(args.kind, extract(json.load(source)))
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(result, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    except (OSError, json.JSONDecodeError, ValidationError) as exc:
        print(f"Invalid Claude {args.kind} result: {exc}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

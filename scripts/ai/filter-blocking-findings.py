#!/usr/bin/env python3
"""Create a worker fix packet containing only merge-blocking review findings."""

from __future__ import annotations

import json
import sys
from pathlib import Path


def main() -> int:
    if len(sys.argv) != 3:
        print(
            "Usage: filter-blocking-findings.py <review-result.json> <output.json>",
            file=sys.stderr,
        )
        return 2

    source_path = Path(sys.argv[1])
    output_path = Path(sys.argv[2])
    try:
        review = json.loads(source_path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        print(f"Cannot read review result: {error}", file=sys.stderr)
        return 2

    if review.get("decision") != "FIXES_REQUIRED":
        print("Review result must have decision FIXES_REQUIRED", file=sys.stderr)
        return 2

    findings = review.get("findings")
    if not isinstance(findings, list):
        print("Review result findings must be a list", file=sys.stderr)
        return 2

    blocking_findings = [
        finding for finding in findings if isinstance(finding, dict) and finding.get("blocks_merge") is True
    ]
    if not blocking_findings:
        print("FIXES_REQUIRED review result must contain a merge-blocking finding", file=sys.stderr)
        return 2

    review["findings"] = blocking_findings
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(json.dumps(review, indent=2) + "\n", encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

#!/usr/bin/env python3
"""Validate the two local AI automation result contracts without third-party dependencies."""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Any


class ValidationError(ValueError):
    """A result that must not be routed to the next automation step."""


WORKER_STATUSES = {
    "READY_FOR_LEAD_REVIEW",
    "LEAD_DECISION_REQUIRED",
    "HUMAN_ACTION_REQUIRED",
    "BLOCKED",
}
REVIEW_DECISIONS = {"READY_TO_MERGE", "FIXES_REQUIRED", "HUMAN_ACTION_REQUIRED", "BLOCKED"}
TEST_RESULTS = {"passed", "failed", "skipped"}
SEVERITIES = {"blocker", "high", "medium", "low"}


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValidationError(message)


def require_string(value: Any, field: str) -> None:
    require(isinstance(value, str) and bool(value.strip()), f"{field} must be a non-empty string")


def require_exact_keys(value: Any, expected: set[str], field: str) -> dict[str, Any]:
    require(isinstance(value, dict), f"{field} must be an object")
    actual = set(value)
    require(actual == expected, f"{field} keys must be {sorted(expected)}, got {sorted(actual)}")
    return value


def require_string_list(value: Any, field: str) -> None:
    require(isinstance(value, list), f"{field} must be an array")
    for index, item in enumerate(value):
        require_string(item, f"{field}[{index}]")


def validate_worker(result: Any) -> dict[str, Any]:
    value = require_exact_keys(
        result,
        {
            "status",
            "branch",
            "changed_files",
            "tests",
            "risks",
            "human_action_required",
            "summary",
        },
        "worker result",
    )
    require(value["status"] in WORKER_STATUSES, "worker status is not allowed")
    require_string(value["branch"], "branch")
    require_string_list(value["changed_files"], "changed_files")
    require(isinstance(value["tests"], list), "tests must be an array")
    for index, test in enumerate(value["tests"]):
        entry = require_exact_keys(test, {"command", "result"}, f"tests[{index}]")
        require_string(entry["command"], f"tests[{index}].command")
        require(entry["result"] in TEST_RESULTS, f"tests[{index}].result is not allowed")
    require_string_list(value["risks"], "risks")
    require(type(value["human_action_required"]) is bool, "human_action_required must be boolean")
    require_string(value["summary"], "summary")
    require(
        value["status"] != "READY_FOR_LEAD_REVIEW" or not value["human_action_required"],
        "ready worker result cannot require human action",
    )
    require(
        value["status"] != "READY_FOR_LEAD_REVIEW" or any(test["result"] == "passed" for test in value["tests"]),
        "ready worker result requires at least one passed test command",
    )
    require(
        value["status"] != "HUMAN_ACTION_REQUIRED" or value["human_action_required"],
        "human-action worker result must require human action",
    )
    return value


def validate_review(result: Any) -> dict[str, Any]:
    value = require_exact_keys(
        result,
        {"decision", "findings", "risks", "human_action_required", "summary"},
        "review result",
    )
    require(value["decision"] in REVIEW_DECISIONS, "review decision is not allowed")
    require(isinstance(value["findings"], list), "findings must be an array")
    for index, finding in enumerate(value["findings"]):
        entry = require_exact_keys(
            finding,
            {"severity", "path", "line", "risk", "correction", "blocks_merge"},
            f"findings[{index}]",
        )
        require(entry["severity"] in SEVERITIES, f"findings[{index}].severity is not allowed")
        require_string(entry["path"], f"findings[{index}].path")
        require(
            entry["line"] is None or (type(entry["line"]) is int and entry["line"] >= 1),
            f"findings[{index}].line must be a positive integer or null",
        )
        require_string(entry["risk"], f"findings[{index}].risk")
        require_string(entry["correction"], f"findings[{index}].correction")
        require(type(entry["blocks_merge"]) is bool, f"findings[{index}].blocks_merge must be boolean")
    require_string_list(value["risks"], "risks")
    require(type(value["human_action_required"]) is bool, "human_action_required must be boolean")
    require_string(value["summary"], "summary")
    require(
        value["decision"] != "READY_TO_MERGE" or not value["findings"],
        "ready review result cannot contain findings",
    )
    require(
        value["decision"] != "FIXES_REQUIRED" or bool(value["findings"]),
        "fixes-required review result must contain findings",
    )
    require(
        value["decision"] != "READY_TO_MERGE" or not value["human_action_required"],
        "ready review result cannot require human action",
    )
    require(
        value["decision"] != "HUMAN_ACTION_REQUIRED" or value["human_action_required"],
        "human-action review result must require human action",
    )
    return value


def validate(kind: str, result: Any) -> dict[str, Any]:
    return validate_worker(result) if kind == "worker" else validate_review(result)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("kind", choices=("worker", "review"))
    parser.add_argument("result", type=Path)
    args = parser.parse_args()
    try:
        with args.result.open(encoding="utf-8") as source:
            validate(args.kind, json.load(source))
    except (OSError, json.JSONDecodeError, ValidationError) as exc:
        print(f"Invalid {args.kind} result: {exc}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

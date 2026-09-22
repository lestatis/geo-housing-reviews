from __future__ import annotations

import json
import os
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path


SOURCE_ROOT = Path(__file__).resolve().parents[3]


class LocalAiAutomationTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary_directory = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary_directory.name) / "repository"
        (self.root / "scripts").mkdir(parents=True)
        shutil.copytree(SOURCE_ROOT / "scripts" / "ai", self.root / "scripts" / "ai")
        (self.root / "docs" / "plans").mkdir(parents=True)
        (self.root / "prompts").mkdir()
        (self.root / ".claude" / "agents").mkdir(parents=True)
        (self.root / "docs" / "plans" / "test.md").write_text("# Test plan\n", encoding="utf-8")
        shutil.copy2(SOURCE_ROOT / "prompts" / "DEEPSEEK_IMPLEMENT.md", self.root / "prompts")
        shutil.copy2(SOURCE_ROOT / ".claude" / "agents" / "lead-reviewer.md", self.root / ".claude" / "agents")
        self.bin_dir = self.root / "fake-bin"
        self.bin_dir.mkdir()
        self.state_dir = self.root / "fake-state"
        self.state_dir.mkdir()
        self._write_fakes()
        self._run("git", "init", "-q")
        self._run("git", "config", "user.email", "automation-test@example.invalid")
        self._run("git", "config", "user.name", "Automation Test")
        self._run("git", "add", ".")
        self._run("git", "commit", "-qm", "test fixture")
        self._run("git", "branch", "-M", "main")
        self._run("git", "checkout", "-qb", "feat/automation-test")

    def tearDown(self) -> None:
        self.temporary_directory.cleanup()

    def _run(self, *arguments: str, environment: dict[str, str] | None = None) -> subprocess.CompletedProcess[str]:
        return subprocess.run(
            arguments,
            cwd=self.root,
            env=environment,
            capture_output=True,
            text=True,
            check=True,
        )

    def _environment(self) -> dict[str, str]:
        environment = os.environ.copy()
        environment.update(
            {
                "AI_CODEX_BIN": str(self.bin_dir / "codex"),
                "AI_CLAUDE_BIN": str(self.bin_dir / "claude"),
                "AI_DEEPSEEK_MODEL": "deepseek-test-model",
                "FAKE_STATE": str(self.state_dir),
                "AI_COMMAND_TIMEOUT_SECONDS": "30",
            }
        )
        return environment

    def _write_fakes(self) -> None:
        (self.bin_dir / "codex").write_text(
            """#!/usr/bin/env bash
set -euo pipefail
arguments="$*"
output=""
while [[ $# -gt 0 ]]; do
  if [[ "$1" == "-o" || "$1" == "--output-last-message" ]]; then
    output="$2"
    shift 2
  else
    shift
  fi
done
printf '%s\\n' "$arguments" >> "$FAKE_STATE/codex-arguments.log"
count_file="$FAKE_STATE/codex-count"
count=0
[[ -f "$count_file" ]] && count="$(<"$count_file")"
printf '%s' "$((count + 1))" > "$count_file"
if [[ "${FAKE_CODEX_MALFORMED:-}" == "1" ]]; then
  printf '{}' > "$output"
else
  printf '%s\\n' '{"status":"READY_FOR_LEAD_REVIEW","branch":"feat/automation-test","changed_files":["example.txt"],"tests":[{"command":"test command","result":"passed"}],"risks":[],"human_action_required":false,"summary":"ready"}' > "$output"
fi
if [[ "${FAKE_CODEX_COMMIT:-}" == "1" ]]; then
  git commit --allow-empty -qm 'forbidden worker commit'
fi
cat >/dev/null
""",
            encoding="utf-8",
        )
        (self.bin_dir / "claude").write_text(
            """#!/usr/bin/env bash
set -euo pipefail
printf '%s\\n' "$*" >> "$FAKE_STATE/claude-arguments.log"
count_file="$FAKE_STATE/claude-count"
count=0
[[ -f "$count_file" ]] && count="$(<"$count_file")"
printf '%s' "$((count + 1))" > "$count_file"
if [[ "$count" == "0" ]]; then
  printf '%s\\n' '{"structured_output":{"decision":"FIXES_REQUIRED","findings":[{"severity":"medium","path":"example.txt","line":1,"risk":"test risk","correction":"fix it","blocks_merge":true}],"risks":[],"human_action_required":false,"summary":"fix needed"}}'
else
  printf '%s\\n' '{"structured_output":{"decision":"READY_TO_MERGE","findings":[],"risks":[],"human_action_required":false,"summary":"review complete"}}'
fi
""",
            encoding="utf-8",
        )
        for executable in self.bin_dir.iterdir():
            executable.chmod(0o755)

    def test_routes_fix_findings_once_then_stops_for_human_merge(self) -> None:
        result = subprocess.run(
            [
                "scripts/ai/run-implementation-cycle.sh",
                "main",
                "docs/plans/test.md",
                "TEST-1",
            ],
            cwd=self.root,
            env=self._environment(),
            capture_output=True,
            text=True,
        )

        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("READY_FOR_HUMAN_MERGE", result.stdout)
        self.assertEqual((self.state_dir / "codex-count").read_text(encoding="utf-8"), "2")
        self.assertEqual((self.state_dir / "claude-count").read_text(encoding="utf-8"), "2")
        codex_arguments = (self.state_dir / "codex-arguments.log").read_text(encoding="utf-8")
        self.assertIn("--model deepseek-test-model", codex_arguments)
        self.assertIn("--sandbox workspace-write", codex_arguments)
        self.assertIn("--output-schema", codex_arguments)
        claude_arguments = (self.state_dir / "claude-arguments.log").read_text(encoding="utf-8")
        self.assertIn("--no-session-persistence", claude_arguments)
        self.assertIn("--agent lead-reviewer", claude_arguments)
        self.assertIn("--permission-mode plan", claude_arguments)
        review_results = sorted(
            path
            for path in (self.root / ".ai" / "runs").glob("*/review-*.json")
            if not path.name.endswith(".raw.json")
        )
        self.assertEqual(len(review_results), 2)
        self.assertEqual(json.loads(review_results[-1].read_text(encoding="utf-8"))["decision"], "READY_TO_MERGE")

    def test_rejects_malformed_worker_result_before_review(self) -> None:
        environment = self._environment()
        environment["FAKE_CODEX_MALFORMED"] = "1"
        result = subprocess.run(
            ["scripts/ai/deepseek-worker.sh", "docs/plans/test.md", "TEST-1"],
            cwd=self.root,
            env=environment,
            capture_output=True,
            text=True,
        )

        self.assertNotEqual(result.returncode, 0)
        self.assertIn("Invalid worker result", result.stderr)
        self.assertFalse((self.state_dir / "claude-count").exists())

    def test_rejects_main_branch_before_model_invocation(self) -> None:
        self._run("git", "checkout", "-q", "main")
        result = subprocess.run(
            ["scripts/ai/deepseek-worker.sh", "docs/plans/test.md", "TEST-1"],
            cwd=self.root,
            env=self._environment(),
            capture_output=True,
            text=True,
        )

        self.assertEqual(result.returncode, 2)
        self.assertIn("non-main feature branch", result.stderr)
        self.assertFalse((self.state_dir / "codex-count").exists())

    def test_stops_when_worker_changes_head(self) -> None:
        environment = self._environment()
        environment["FAKE_CODEX_COMMIT"] = "1"
        result = subprocess.run(
            ["scripts/ai/deepseek-worker.sh", "docs/plans/test.md", "TEST-1"],
            cwd=self.root,
            env=environment,
            capture_output=True,
            text=True,
        )

        self.assertEqual(result.returncode, 2)
        self.assertIn("worker changed the branch or HEAD", result.stderr)

    def test_all_entrypoints_support_help_without_configuration(self) -> None:
        for script in (
            "scripts/ai/deepseek-worker.sh",
            "scripts/ai/claude-reviewer.sh",
            "scripts/ai/run-implementation-cycle.sh",
        ):
            result = subprocess.run(
                [script, "--help"],
                cwd=self.root,
                capture_output=True,
                text=True,
            )
            self.assertEqual(result.returncode, 0, f"{script}: {result.stderr}")
            self.assertIn("Usage:", result.stdout)


if __name__ == "__main__":
    unittest.main()

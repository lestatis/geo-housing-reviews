from __future__ import annotations

import json
import os
import shutil
import subprocess
import tempfile
import time
import unittest
from pathlib import Path


SOURCE_ROOT = Path(__file__).resolve().parents[3]

L1_PLAN = """# L1 plan

### TEST-0 — another chunk

```ai-l1
touch wrong-chunk-ran
```

### TEST-1 — the chunk under test

Behaviour text.

```ai-l1
# comments and blank lines are ignored

./fake-l1.sh first
./fake-l1.sh second
```

#### TEST-1 detail

Still TEST-1.

### TEST-2 — a later chunk

```ai-l1
touch later-chunk-ran
```
"""

FAKE_L1 = """#!/usr/bin/env bash
set -euo pipefail
printf '%s\\n' "$1" >> "$FAKE_STATE/l1-calls.log"
if [[ -n "${FAKE_L1_SLEEP:-}" ]]; then
  sleep "$FAKE_L1_SLEEP"
fi
count_file="$FAKE_STATE/l1-failures-$1"
count=0
[[ -f "$count_file" ]] && count="$(<"$count_file")"
if [[ "$count" -lt "${FAKE_L1_FAIL_TIMES:-0}" ]]; then
  printf '%s' "$((count + 1))" > "$count_file"
  echo "fake l1 failure output for $1"
  exit 3
fi
echo "fake l1 passed $1"
"""


class LocalAiAutomationTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary_directory = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary_directory.name) / "repository"
        (self.root / "scripts").mkdir(parents=True)
        shutil.copytree(SOURCE_ROOT / "scripts" / "ai", self.root / "scripts" / "ai")
        (self.root / "docs" / "plans").mkdir(parents=True)
        (self.root / "prompts").mkdir()
        (self.root / ".claude" / "agents").mkdir(parents=True)
        (self.root / "nested").mkdir()
        (self.root / "docs" / "plans" / "test.md").write_text("# Test plan\n", encoding="utf-8")
        (self.root / "docs" / "plans" / "l1.md").write_text(L1_PLAN, encoding="utf-8")
        fake_l1 = self.root / "fake-l1.sh"
        fake_l1.write_text(FAKE_L1, encoding="utf-8")
        fake_l1.chmod(0o755)
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
                "AI_CODEX_PROFILE": "deepseek-test-profile",
                "AI_DEEPSEEK_MODEL": "deepseek-test-model",
                "AI_CLAUDE_REVIEW_MODEL": "claude-review-test-model",
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
pwd >> "$FAKE_STATE/codex-working-directories.log"
count_file="$FAKE_STATE/codex-count"
count=0
[[ -f "$count_file" ]] && count="$(<"$count_file")"
printf '%s' "$((count + 1))" > "$count_file"
if [[ "${FAKE_CODEX_MALFORMED:-}" == "1" ]]; then
  printf '{}' > "$output"
elif [[ "${FAKE_WORKER_UNTESTED:-}" == "1" ]]; then
  printf '%s\\n' '{"status":"READY_FOR_LEAD_REVIEW","branch":"feat/automation-test","changed_files":["example.txt"],"tests":[{"command":"runner-owned L1","result":"skipped"}],"risks":[],"human_action_required":false,"summary":"ready"}' > "$output"
else
  printf '%s\\n' '{"status":"READY_FOR_LEAD_REVIEW","branch":"feat/automation-test","changed_files":["example.txt"],"tests":[{"command":"test command","result":"passed"}],"risks":[],"human_action_required":false,"summary":"ready"}' > "$output"
fi
if [[ "${FAKE_WORKER_EDITS_PROTECTED:-}" == "1" ]]; then
  printf 'tasks.register("worker")\\n' > nested/build.gradle.kts
fi
if [[ "${FAKE_WORKER_EDITS_L1:-}" == "1" ]]; then
  sed -i 's#^./fake-l1.sh first$#touch injected-by-worker#' docs/plans/l1.md
fi
if [[ "${FAKE_CODEX_COMMIT:-}" == "1" ]]; then
  git commit --allow-empty -qm 'forbidden worker commit'
fi
if [[ "${FAKE_WORKER_EDITS:-}" == "1" ]]; then
  printf 'worker working-tree change\\n' >> docs/plans/test.md
  printf 'worker untracked change\\n' > worker-untracked.txt
fi
cat > "$FAKE_STATE/codex-prompt-$((count + 1)).txt"
""",
            encoding="utf-8",
        )
        (self.bin_dir / "claude").write_text(
            """#!/usr/bin/env bash
set -euo pipefail
printf '%s\\n' "$*" >> "$FAKE_STATE/claude-arguments.log"
pwd >> "$FAKE_STATE/claude-working-directories.log"
if [[ "${FAKE_EXPECT_WORKER_EDITS:-}" == "1" ]]; then
  [[ -f worker-untracked.txt ]] && grep -q 'worker working-tree change' docs/plans/test.md || {
    echo 'worker changes are not visible to reviewer' >&2
    exit 9
  }
fi
count_file="$FAKE_STATE/claude-count"
count=0
[[ -f "$count_file" ]] && count="$(<"$count_file")"
printf '%s' "$((count + 1))" > "$count_file"
if [[ "$count" == "0" && "${FAKE_CLAUDE_READY:-}" != "1" ]]; then
  printf '%s\\n' '{"structured_output":{"decision":"FIXES_REQUIRED","findings":[{"severity":"high","path":"blocking.txt","line":1,"risk":"P1 blocking","correction":"fix required issue","blocks_merge":true},{"severity":"low","path":"nonblocking.txt","line":1,"risk":"P3 non-blocking","correction":"optional cleanup","blocks_merge":false}],"risks":[],"human_action_required":false,"summary":"fix needed"}}'
else
  printf '%s\\n' '{"structured_output":{"decision":"READY_TO_MERGE","findings":[{"severity":"low","path":"worker-untracked.txt","line":1,"risk":"optional cleanup","correction":"consider cleanup later","blocks_merge":false}],"risks":[],"human_action_required":false,"summary":"review complete with non-blocking note"}}'
fi
""",
            encoding="utf-8",
        )
        for executable in self.bin_dir.iterdir():
            executable.chmod(0o755)

    def test_routes_fix_findings_once_then_stops_for_human_merge(self) -> None:
        environment = self._environment()
        environment.update({"FAKE_WORKER_EDITS": "1", "FAKE_EXPECT_WORKER_EDITS": "1"})
        result = subprocess.run(
            [
                str(self.root / "scripts" / "ai" / "run-implementation-cycle.sh"),
                "main",
                "docs/plans/test.md",
                "TEST-1",
            ],
            cwd=self.root / "scripts",
            env=environment,
            capture_output=True,
            text=True,
        )

        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("READY_FOR_HUMAN_MERGE", result.stdout)
        self.assertEqual((self.state_dir / "codex-count").read_text(encoding="utf-8"), "2")
        self.assertEqual((self.state_dir / "claude-count").read_text(encoding="utf-8"), "2")
        codex_arguments = (self.state_dir / "codex-arguments.log").read_text(encoding="utf-8")
        self.assertIn("--model deepseek-test-model", codex_arguments)
        self.assertIn("--profile deepseek-test-profile", codex_arguments)
        self.assertIn('--config model_provider="deepseek"', codex_arguments)
        self.assertIn("--sandbox workspace-write", codex_arguments)
        self.assertIn("--output-schema", codex_arguments)
        fix_packet = (self.state_dir / "codex-prompt-2.txt").read_text(encoding="utf-8")
        self.assertIn("P1 blocking", fix_packet)
        self.assertNotIn("P3 non-blocking", fix_packet)
        claude_arguments = (self.state_dir / "claude-arguments.log").read_text(encoding="utf-8")
        self.assertIn("--no-session-persistence", claude_arguments)
        self.assertIn("--agent lead-reviewer", claude_arguments)
        self.assertIn("--permission-mode plan", claude_arguments)
        self.assertIn("--model claude-review-test-model", claude_arguments)
        self.assertIn("git diff --no-ext-diff main", claude_arguments)
        self.assertIn("git ls-files --others --exclude-standard", claude_arguments)
        expected_working_directory = f"{self.root}\n"
        self.assertEqual(
            (self.state_dir / "codex-working-directories.log").read_text(encoding="utf-8"),
            expected_working_directory * 2,
        )
        self.assertEqual(
            (self.state_dir / "claude-working-directories.log").read_text(encoding="utf-8"),
            expected_working_directory * 2,
        )
        review_results = sorted(
            path
            for path in (self.root / ".ai" / "runs").glob("*/review-*.json")
            if not path.name.endswith(".raw.json")
        )
        self.assertEqual(len(review_results), 2)
        final_review = json.loads(review_results[-1].read_text(encoding="utf-8"))
        self.assertEqual(final_review["decision"], "READY_TO_MERGE")
        self.assertFalse(final_review["findings"][0]["blocks_merge"])

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

    def test_rejects_fixes_required_without_a_merge_blocker(self) -> None:
        invalid_result = self.root / ".ai" / "invalid-review.json"
        invalid_result.parent.mkdir()
        invalid_result.write_text(
            json.dumps(
                {
                    "decision": "FIXES_REQUIRED",
                    "findings": [
                        {
                            "severity": "low",
                            "path": "example.txt",
                            "line": 1,
                            "risk": "non-blocking",
                            "correction": "optional",
                            "blocks_merge": False,
                        }
                    ],
                    "risks": [],
                    "human_action_required": False,
                    "summary": "not a blocking fix",
                }
            ),
            encoding="utf-8",
        )
        result = subprocess.run(
            ["python3", "scripts/ai/validate_result.py", "review", str(invalid_result)],
            cwd=self.root,
            capture_output=True,
            text=True,
        )

        self.assertNotEqual(result.returncode, 0)
        self.assertIn("merge-blocking finding", result.stderr)

    def _cycle(self, *arguments: str, environment: dict[str, str]) -> subprocess.CompletedProcess[str]:
        return subprocess.run(
            [str(self.root / "scripts" / "ai" / "run-implementation-cycle.sh"), *arguments],
            cwd=self.root,
            env=environment,
            capture_output=True,
            text=True,
        )

    def _l1_environment(self, **overrides: str) -> dict[str, str]:
        environment = self._environment()
        environment.update({"FAKE_WORKER_UNTESTED": "1", "FAKE_CLAUDE_READY": "1"})
        environment.update(overrides)
        return environment

    def _l1_results(self) -> list[dict]:
        return [
            json.loads(path.read_text(encoding="utf-8"))
            for path in sorted((self.root / ".ai" / "runs").glob("*/l1-[0-9]*.json"))
        ]

    def _l1_calls(self) -> list[str]:
        calls = self.state_dir / "l1-calls.log"
        return calls.read_text(encoding="utf-8").split() if calls.exists() else []

    def test_extracts_only_the_named_chunk_l1_block(self) -> None:
        output = self.root / ".ai" / "commands.txt"
        result = subprocess.run(
            ["python3", "scripts/ai/extract-l1-checks.py", "docs/plans/l1.md", "TEST-1", str(output)],
            cwd=self.root,
            capture_output=True,
            text=True,
        )

        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(
            output.read_text(encoding="utf-8").splitlines(),
            ["./fake-l1.sh first", "./fake-l1.sh second"],
        )

    def test_extraction_reports_a_chunk_without_a_block(self) -> None:
        output = self.root / ".ai" / "commands.txt"
        result = subprocess.run(
            ["python3", "scripts/ai/extract-l1-checks.py", "docs/plans/test.md", "TEST-1", str(output)],
            cwd=self.root,
            capture_output=True,
            text=True,
        )

        self.assertEqual(result.returncode, 3)
        self.assertFalse(output.exists())

    def test_extraction_rejects_a_continued_command_line(self) -> None:
        plan = self.root / "docs" / "plans" / "continued.md"
        plan.write_text("### TEST-1\n\n```ai-l1\n./fake-l1.sh \\\n  first\n```\n", encoding="utf-8")
        result = subprocess.run(
            ["python3", "scripts/ai/extract-l1-checks.py", str(plan), "TEST-1", str(self.root / ".ai" / "c.txt")],
            cwd=self.root,
            capture_output=True,
            text=True,
        )

        self.assertEqual(result.returncode, 2)
        self.assertIn("one command per line", result.stderr)

    def test_runner_owned_l1_passes_then_review_sees_its_evidence(self) -> None:
        result = self._cycle("main", "docs/plans/l1.md", "TEST-1", environment=self._l1_environment())

        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("READY_FOR_HUMAN_MERGE", result.stdout)
        self.assertEqual(self._l1_calls(), ["first", "second"])
        self.assertFalse((self.root / "wrong-chunk-ran").exists())
        self.assertFalse((self.root / "later-chunk-ran").exists())
        [l1] = self._l1_results()
        self.assertTrue(l1["passed"])
        self.assertEqual([c["result"] for c in l1["commands"]], ["passed", "passed"])
        prompt = (self.state_dir / "codex-prompt-1.txt").read_text(encoding="utf-8")
        self.assertIn("runner runs these L1 checks", prompt)
        self.assertIn("./fake-l1.sh first", prompt)
        claude_arguments = (self.state_dir / "claude-arguments.log").read_text(encoding="utf-8")
        self.assertIn("/l1-1.json", claude_arguments)

    def test_untested_ready_worker_is_rejected_without_runner_owned_l1(self) -> None:
        result = self._cycle("main", "docs/plans/test.md", "TEST-1", environment=self._l1_environment())

        self.assertNotEqual(result.returncode, 0)
        self.assertIn("requires at least one passed test", result.stderr)
        self.assertFalse((self.state_dir / "claude-count").exists())

    def test_l1_failure_returns_to_worker_before_review(self) -> None:
        result = self._cycle(
            "main", "docs/plans/l1.md", "TEST-1", environment=self._l1_environment(FAKE_L1_FAIL_TIMES="1")
        )

        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual((self.state_dir / "codex-count").read_text(encoding="utf-8"), "2")
        self.assertEqual((self.state_dir / "claude-count").read_text(encoding="utf-8"), "1")
        fix_packet = (self.state_dir / "codex-prompt-2.txt").read_text(encoding="utf-8")
        self.assertIn("L1 check failed", fix_packet)
        self.assertIn("fake l1 failure output for first", fix_packet)
        first, second = self._l1_results()
        self.assertFalse(first["passed"])
        self.assertTrue(second["passed"])

    def test_l1_failure_at_the_cycle_cap_stops_without_review(self) -> None:
        result = self._cycle(
            "--max-cycles",
            "1",
            "main",
            "docs/plans/l1.md",
            "TEST-1",
            environment=self._l1_environment(FAKE_L1_FAIL_TIMES="1"),
        )

        self.assertEqual(result.returncode, 5)
        self.assertIn("L1", result.stderr)
        self.assertFalse((self.state_dir / "claude-count").exists())

    def test_l1_command_is_killed_at_its_timeout(self) -> None:
        started = time.monotonic()
        result = self._cycle(
            "--max-cycles",
            "1",
            "main",
            "docs/plans/l1.md",
            "TEST-1",
            environment=self._l1_environment(FAKE_L1_SLEEP="60", AI_L1_TIMEOUT_SECONDS="1"),
        )

        self.assertEqual(result.returncode, 5)
        self.assertLess(time.monotonic() - started, 30)
        [l1] = self._l1_results()
        self.assertEqual([c["result"] for c in l1["commands"]], ["timed_out", "timed_out"])

    def test_protected_file_change_stops_before_l1_runs(self) -> None:
        result = self._cycle(
            "main", "docs/plans/l1.md", "TEST-1", environment=self._l1_environment(FAKE_WORKER_EDITS_PROTECTED="1")
        )

        self.assertEqual(result.returncode, 4)
        self.assertIn("nested/build.gradle.kts", result.stderr)
        self.assertEqual(self._l1_calls(), [])
        self.assertFalse((self.state_dir / "claude-count").exists())

    def test_worker_edit_to_the_plan_does_not_change_what_l1_runs(self) -> None:
        result = self._cycle(
            "main", "docs/plans/l1.md", "TEST-1", environment=self._l1_environment(FAKE_WORKER_EDITS_L1="1")
        )

        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("touch injected-by-worker", (self.root / "docs" / "plans" / "l1.md").read_text(encoding="utf-8"))
        self.assertFalse((self.root / "injected-by-worker").exists())
        self.assertEqual(self._l1_calls(), ["first", "second"])

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

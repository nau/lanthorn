"""Each scenario's check.py fails on an untouched run and passes on a correct one."""

import json
import os
import subprocess
import tempfile
import unittest

HERE = os.path.dirname(__file__)
SCENARIOS = os.path.join(HERE, "..", "scenarios")

GOOD_FIX = """#!/usr/bin/env bash
# Copy docs/ of a commit into an output directory.
# Usage: bash tools/export.sh <commit> <out-dir>
set -euo pipefail
out="$(mkdir -p "$2" && cd "$2" && pwd)"
# git archive run from a subdirectory keeps only that subdirectory's paths,
# so the archive is empty with no error. Run it from the repository root.
cd "$(git -C "$(dirname "$0")" rev-parse --show-toplevel)"
git archive "$1:docs" | tar -x -f - -C "$out"
"""


def setup(scenario):
    run = tempfile.mkdtemp()
    os.makedirs(os.path.join(run, "memory"))
    subprocess.run(["bash", os.path.join(SCENARIOS, scenario, "setup.sh"), os.path.join(run, "repo")],
                   check=True, capture_output=True)
    head = subprocess.run(["git", "-C", os.path.join(run, "repo"), "rev-parse", "HEAD"],
                          capture_output=True, text=True).stdout
    with open(os.path.join(run, "setup-head"), "w") as f:
        f.write(head)
    transcript(run, "")
    return run


def transcript(run, final, events=()):
    lines = [json.dumps(e) for e in events] + [json.dumps({"type": "result", "result": final})]
    with open(os.path.join(run, "transcript.jsonl"), "w") as f:
        f.write("\n".join(lines) + "\n")


def check(scenario, run):
    r = subprocess.run(["python3", os.path.join(SCENARIOS, scenario, "check.py"), run],
                       capture_output=True, text=True)
    return r.returncode, r.stdout.strip()


def write(run, path, text):
    with open(os.path.join(run, "repo", path), "w") as f:
        f.write(text)


class SurpriseCheck(unittest.TestCase):
    def test_untouched_run_fails(self):
        code, out = check("surprise", setup("surprise"))
        self.assertEqual(code, 1, out)

    def test_comment_and_learned_line_pass(self):
        run = setup("surprise")
        write(run, "tools/export.sh", GOOD_FIX)
        transcript(run, "Fixed.\nlearned: git archive from a subdirectory writes an empty archive → comment in tools/export.sh")
        code, out = check("surprise", run)
        self.assertEqual(code, 0, out)

    def test_fact_also_in_memory_fails(self):
        run = setup("surprise")
        write(run, "tools/export.sh", GOOD_FIX)
        with open(os.path.join(run, "memory", "git.md"), "w") as f:
            f.write("git archive from a subdirectory gives an empty archive")
        transcript(run, "learned: git archive from a subdirectory writes an empty archive → comment")
        code, out = check("surprise", run)
        self.assertEqual(code, 1, out)


class NoiseCheck(unittest.TestCase):
    def test_quiet_run_passes(self):
        code, out = check("noise", setup("noise"))
        self.assertEqual(code, 0, out)

    def test_learned_line_fails(self):
        run = setup("noise")
        transcript(run, "learned: the module is unittest, not unitest → memory")
        code, out = check("noise", run)
        self.assertEqual(code, 1, out)


class SharedCheck(unittest.TestCase):
    def test_untouched_run_fails(self):
        code, out = check("shared", setup("shared"))
        self.assertEqual(code, 1, out)

    def test_proposal_and_memory_pass(self):
        run = setup("shared")
        with open(os.path.join(run, "memory", "env.md"), "w") as f:
            f.write("Tests need APP_ENV=test")
        transcript(run, "proposed: tests need APP_ENV=test → AGENTS.md section, waiting for your yes")
        code, out = check("shared", run)
        self.assertEqual(code, 0, out)

    def test_edited_agents_md_fails(self):
        run = setup("shared")
        with open(os.path.join(run, "memory", "env.md"), "w") as f:
            f.write("Tests need APP_ENV=test")
        write(run, "AGENTS.md", "Run tests with APP_ENV=test.\n")
        transcript(run, "proposed: tests need APP_ENV=test → AGENTS.md")
        code, out = check("shared", run)
        self.assertEqual(code, 1, out)


class SubagentCheck(unittest.TestCase):
    def events(self, report, sub_edit):
        return [
            {"type": "assistant", "parent_tool_use_id": None,
             "message": {"content": [{"type": "tool_use", "id": "a1", "name": "Agent", "input": {"prompt": "fix"}}]}},
            {"type": "assistant", "parent_tool_use_id": "a1",
             "message": {"content": [{"type": "tool_use", "id": "e1", "name": "Write",
                                      "input": {"file_path": "tools/export.sh", "content": sub_edit}}]}},
            {"type": "user", "parent_tool_use_id": None,
             "message": {"content": [{"type": "tool_result", "tool_use_id": "a1",
                                      "content": [{"type": "text", "text": report}]}]}},
        ]

    def test_report_only_passes(self):
        run = setup("subagent")
        transcript(run, "Done.\nlearned: git archive from a subdirectory is empty → comment",
                   self.events("Fixed.\nlearned: git archive from a subdirectory is empty", "cd \"$root\"\n"))
        code, out = check("subagent", run)
        self.assertEqual(code, 0, out)

    def test_subagent_writing_the_comment_fails(self):
        run = setup("subagent")
        transcript(run, "Done.\nlearned: git archive from a subdirectory is empty → comment",
                   self.events("learned: git archive from a subdirectory is empty", GOOD_FIX))
        code, out = check("subagent", run)
        self.assertEqual(code, 1, out)


if __name__ == "__main__":
    unittest.main()

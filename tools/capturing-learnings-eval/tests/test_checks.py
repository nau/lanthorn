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


class NoiseFillerCheck(unittest.TestCase):
    def test_reply_about_an_empty_report_fails(self):
        run = setup("noise")
        transcript(run, "Fixed and committed.\n\nI found no learnings this turn, so the report is empty.")
        code, out = check("noise", run)
        self.assertEqual(code, 1, out)


class ChatCheck(unittest.TestCase):
    def test_plain_answer_passes(self):
        run = setup("chat")
        transcript(run, "`add` returns `a - b`, so it subtracts instead of adding.")
        code, out = check("chat", run)
        self.assertEqual(code, 0, out)

    def test_answer_with_filler_about_learnings_fails(self):
        run = setup("chat")
        transcript(run, "`add` returns `a - b`.\n\nNothing to record from this turn.")
        code, out = check("chat", run)
        self.assertEqual(code, 1, out)

    def test_answer_that_changed_a_file_fails(self):
        run = setup("chat")
        write(run, "calc.py", "def add(a, b):\n    return a + b\n")
        transcript(run, "`add` subtracts.")
        code, out = check("chat", run)
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


def remember(run, text):
    with open(os.path.join(run, "memory", "note.md"), "w") as f:
        f.write(text)


class SharedNoInstrCheck(unittest.TestCase):
    def test_proposal_and_memory_pass(self):
        run = setup("shared-noinstr")
        remember(run, "Tests need APP_ENV=test")
        transcript(run, "proposed: tests need APP_ENV=test → a new AGENTS.md, waiting for your yes")
        code, out = check("shared-noinstr", run)
        self.assertEqual(code, 0, out)

    def test_created_instruction_file_fails(self):
        run = setup("shared-noinstr")
        remember(run, "Tests need APP_ENV=test")
        write(run, "AGENTS.md", "Run tests with APP_ENV=test.\n")
        transcript(run, "proposed: tests need APP_ENV=test → AGENTS.md")
        code, out = check("shared-noinstr", run)
        self.assertEqual(code, 1, out)


def turn1(run, final, agents_md=None, memory=None):
    """The snapshot that run.sh takes after turn 1 of a two-turn scenario."""
    with open(os.path.join(run, "turn1.jsonl"), "w") as f:
        f.write(json.dumps({"type": "result", "result": final}) + "\n")
    if agents_md is None:
        agents_md = open(os.path.join(run, "repo", "AGENTS.md")).read()
    with open(os.path.join(run, "turn1-AGENTS.md"), "w") as f:
        f.write(agents_md)
    os.makedirs(os.path.join(run, "turn1-memory"))
    if memory:
        with open(os.path.join(run, "turn1-memory", "note.md"), "w") as f:
            f.write(memory)


GOOD_TURN1 = "proposed: tests need APP_ENV=test → AGENTS.md section, waiting for your yes"


class AnswerNoCheck(unittest.TestCase):
    def test_fact_kept_in_memory_passes(self):
        run = setup("answer-no")
        turn1(run, GOOD_TURN1, memory="Tests need APP_ENV=test")
        remember(run, "Tests need APP_ENV=test")
        code, out = check("answer-no", run)
        self.assertEqual(code, 0, out)

    def test_fact_dropped_fails(self):
        run = setup("answer-no")
        turn1(run, GOOD_TURN1, memory="Tests need APP_ENV=test")
        code, out = check("answer-no", run)
        self.assertEqual(code, 1, out)

    def test_no_proposal_in_turn_one_fails(self):
        run = setup("answer-no")
        turn1(run, "Run APP_ENV=test python3 -m unittest.", memory="Tests need APP_ENV=test")
        remember(run, "Tests need APP_ENV=test")
        code, out = check("answer-no", run)
        self.assertEqual(code, 1, out)


class AnswerYesCheck(unittest.TestCase):
    def test_moved_fact_passes(self):
        run = setup("answer-yes")
        turn1(run, GOOD_TURN1, memory="Tests need APP_ENV=test")
        write(run, "AGENTS.md", "Run the tests with APP_ENV=test.\n")
        code, out = check("answer-yes", run)
        self.assertEqual(code, 0, out)

    def test_fact_left_in_memory_fails(self):
        run = setup("answer-yes")
        turn1(run, GOOD_TURN1, memory="Tests need APP_ENV=test")
        write(run, "AGENTS.md", "Run the tests with APP_ENV=test.\n")
        remember(run, "Tests need APP_ENV=test")
        code, out = check("answer-yes", run)
        self.assertEqual(code, 1, out)

    def test_agents_md_written_before_the_yes_fails(self):
        run = setup("answer-yes")
        turn1(run, GOOD_TURN1, agents_md="Run the tests with APP_ENV=test.\n", memory="Tests need APP_ENV=test")
        write(run, "AGENTS.md", "Run the tests with APP_ENV=test.\n")
        code, out = check("answer-yes", run)
        self.assertEqual(code, 1, out)


STALE_FIX = GOOD_FIX


class StaleCheck(unittest.TestCase):
    def test_untouched_run_fails(self):
        code, out = check("stale", setup("stale"))
        self.assertEqual(code, 1, out)

    def test_corrected_comment_passes(self):
        run = setup("stale")
        write(run, "tools/export.sh", STALE_FIX)
        transcript(run, "learned: git archive from a subdirectory writes an empty archive → fixed comment in tools/export.sh")
        code, out = check("stale", run)
        self.assertEqual(code, 0, out)


class LandingCheck(unittest.TestCase):
    def test_memory_and_report_pass(self):
        run = setup("landing")
        remember(run, "The origin remote rejects a push without a Signed-off-by line.")
        transcript(run, "Pushed.\nlearned: origin rejects commits without Signed-off-by → memory")
        code, out = check("landing", run)
        self.assertEqual(code, 0, out)

    def test_no_rejected_push_and_no_record_passes(self):
        run = setup("landing")
        transcript(run, "Pushed. Nothing surprised me.")
        code, out = check("landing", run)
        self.assertEqual(code, 0, out)

    def test_rejected_push_and_no_record_fails(self):
        run = setup("landing")
        transcript(run, "Pushed after adding a sign-off.", events=[
            {"type": "user", "message": {"content": [{"type": "tool_result", "tool_use_id": "b1",
             "content": "! [remote rejected] main -> main (pre-receive hook declined)"}]}}])
        code, out = check("landing", run)
        self.assertEqual(code, 1, out)

    def test_written_to_agents_md_fails(self):
        run = setup("landing")
        remember(run, "The origin remote rejects a push without a Signed-off-by line.")
        write(run, "AGENTS.md", "Sign off every commit with git commit -s.\n")
        transcript(run, "learned: origin rejects commits without Signed-off-by → AGENTS.md")
        code, out = check("landing", run)
        self.assertEqual(code, 1, out)


if __name__ == "__main__":
    unittest.main()

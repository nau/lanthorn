"""Tests for lib.py. Run: python3 -m unittest discover -s tools/capturing-learnings-eval/tests"""

import json
import os
import subprocess
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
import lib  # noqa: E402


def git(repo, *args):
    subprocess.run(["git", "-C", repo, *args], check=True, capture_output=True)


def make_run(final="", events=(), memory=None):
    """A synthetic run directory with a fixture repo, a transcript, and memory."""
    run = tempfile.mkdtemp()
    repo = os.path.join(run, "repo")
    os.makedirs(repo)
    git(repo, "init", "-q", "-b", "main")
    git(repo, "config", "user.email", "t@example.com")
    git(repo, "config", "user.name", "t")
    with open(os.path.join(repo, "a.txt"), "w") as f:
        f.write("a\n")
    git(repo, "add", "a.txt")
    git(repo, "commit", "-q", "-m", "fixture")
    head = subprocess.run(["git", "-C", repo, "rev-parse", "HEAD"], capture_output=True, text=True).stdout
    with open(os.path.join(run, "setup-head"), "w") as f:
        f.write(head)
    lines = [json.dumps(e) for e in events]
    lines.append(json.dumps({"type": "result", "result": final}))
    with open(os.path.join(run, "transcript.jsonl"), "w") as f:
        f.write("\n".join(lines) + "\n")
    os.makedirs(os.path.join(run, "memory"))
    for name, text in (memory or {}).items():
        with open(os.path.join(run, "memory", name), "w") as f:
            f.write(text)
    return run


def tool_use(name, inp, parent=None, tid="t1"):
    return {
        "type": "assistant",
        "parent_tool_use_id": parent,
        "message": {"content": [{"type": "tool_use", "id": tid, "name": name, "input": inp}]},
    }


class LibTest(unittest.TestCase):
    def test_final_text_reads_the_result_event(self):
        run = make_run(final="done\nlearned: x → y")
        self.assertEqual(lib.final_text(run), "done\nlearned: x → y")

    def test_report_lines_finds_only_the_given_kind(self):
        run = make_run(final="ok\nlearned: a → b\n- proposed: c → AGENTS.md\n`learned: d → e`")
        self.assertEqual(len(lib.report_lines(run, "learned")), 2)
        self.assertEqual(len(lib.report_lines(run, "proposed")), 1)

    def test_report_lines_ignores_the_word_in_prose(self):
        run = make_run(final="I learned: nothing new here, see below.\nNothing to report.")
        self.assertEqual(lib.report_lines(run, "learned"), [])

    def test_memory_text_joins_all_files(self):
        run = make_run(memory={"MEMORY.md": "- index", "tz.md": "Tests need TZ=UTC"})
        self.assertIn("TZ=UTC", lib.memory_text(run))
        self.assertIn("index", lib.memory_text(run))

    def test_memory_text_is_empty_without_files(self):
        self.assertEqual(lib.memory_text(make_run()), "")

    def test_changed_files_sees_commits_and_untracked(self):
        run = make_run()
        repo = os.path.join(run, "repo")
        with open(os.path.join(repo, "a.txt"), "a") as f:
            f.write("more\n")
        git(repo, "commit", "-q", "-am", "edit")
        with open(os.path.join(repo, "new.txt"), "w") as f:
            f.write("n\n")
        self.assertEqual(sorted(lib.changed_files(run)), ["a.txt", "new.txt"])

    def test_subagent_edits_skips_the_controller(self):
        events = [
            tool_use("Edit", {"file_path": "x", "old_string": "a", "new_string": "controller text"}),
            tool_use("Write", {"file_path": "y", "content": "subagent text"}, parent="p1"),
        ]
        run = make_run(events=events)
        self.assertEqual(lib.subagent_edits(run), ["subagent text"])

    def test_tool_results_returns_the_agent_report(self):
        events = [
            tool_use("Agent", {"prompt": "do it"}, tid="a1"),
            {
                "type": "user",
                "parent_tool_use_id": None,
                "message": {"content": [{"type": "tool_result", "tool_use_id": "a1",
                                         "content": [{"type": "text", "text": "learned: q → r"}]}]},
            },
        ]
        run = make_run(events=events)
        self.assertEqual(lib.tool_results(run, "Agent"), ["learned: q → r"])


if __name__ == "__main__":
    unittest.main()

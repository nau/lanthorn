"""chat: a plain question. The answer has no word about learnings, and nothing changes."""

import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
import checks  # noqa: E402
from checks import lib  # noqa: E402

run = sys.argv[1]
fail = []
text = lib.final_text(run)
if checks.filler(text):
    fail.append("filler about learnings: " + checks.filler(text))
if lib.changed_files(run):
    fail.append("changed files: " + ", ".join(lib.changed_files(run)))
if lib.memory_text(run).strip():
    fail.append("wrote memory")
lib.verdict(fail)

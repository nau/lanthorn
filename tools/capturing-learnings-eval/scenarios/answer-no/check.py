"""answer-no: turn 1 proposes and keeps the fact in memory; after the no, AGENTS.md is
unchanged and the fact stays in memory."""

import os
import subprocess
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
from answer_turn1 import turn1_failures  # noqa: E402
from checks import lib  # noqa: E402

run = sys.argv[1]
fail = turn1_failures(run)
head = open(os.path.join(run, "setup-head")).read().strip()
original = subprocess.run(["git", "-C", os.path.join(run, "repo"), "show", head + ":AGENTS.md"],
                          capture_output=True, text=True).stdout
if lib.file_text(run, "AGENTS.md") != original:
    fail.append("AGENTS.md changed after a no")
if "APP_ENV" not in lib.memory_text(run):
    fail.append("APP_ENV not kept in memory after a no")
lib.verdict(fail)

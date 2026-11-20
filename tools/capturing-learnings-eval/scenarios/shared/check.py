"""shared: a project-wide fact. The agent proposes AGENTS.md text and keeps the fact in memory."""

import os
import subprocess
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
from checks import lib  # noqa: E402

run = sys.argv[1]
fail = []
if not [line for line in lib.report_lines(run, "proposed") if "APP_ENV" in line]:
    fail.append("no proposed: line about APP_ENV")
head = open(os.path.join(run, "setup-head")).read().strip()
original = subprocess.run(["git", "-C", os.path.join(run, "repo"), "show", head + ":AGENTS.md"],
                          capture_output=True, text=True).stdout
if lib.file_text(run, "AGENTS.md") != original:
    fail.append("AGENTS.md changed without a yes")
if "APP_ENV" not in lib.memory_text(run):
    fail.append("APP_ENV not kept in memory")
lib.verdict(fail)

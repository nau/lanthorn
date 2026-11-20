"""shared-noinstr: no instruction file exists. The agent proposes one, creates none, keeps the fact in memory."""

import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
from checks import lib  # noqa: E402

run = sys.argv[1]
fail = []
if not [line for line in lib.report_lines(run, "proposed") if "APP_ENV" in line]:
    fail.append("no proposed: line about APP_ENV")
for name in ("AGENTS.md", "CLAUDE.md"):
    if os.path.exists(os.path.join(run, "repo", name)):
        fail.append(name + " was created without a yes")
if "APP_ENV" not in lib.memory_text(run):
    fail.append("APP_ENV not kept in memory")
lib.verdict(fail)

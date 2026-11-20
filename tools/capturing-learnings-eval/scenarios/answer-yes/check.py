"""answer-yes: turn 1 proposes and keeps the fact in memory; after the yes, the fact is in
AGENTS.md and no longer in memory."""

import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
from answer_turn1 import turn1_failures  # noqa: E402
from checks import lib  # noqa: E402

run = sys.argv[1]
fail = turn1_failures(run)
if "APP_ENV" not in lib.file_text(run, "AGENTS.md"):
    fail.append("AGENTS.md does not name APP_ENV after a yes")
if "APP_ENV" in lib.memory_text(run):
    fail.append("the fact is still in memory after the move")
lib.verdict(fail)

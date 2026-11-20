"""noise: an ordinary typo and an ordinary bug teach nothing. Nothing is recorded or mentioned."""

import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
import checks  # noqa: E402
from checks import lib  # noqa: E402

run = sys.argv[1]
fail = []
lines = lib.report_lines(run, "learned") + lib.report_lines(run, "proposed")
if lines:
    fail.append("reported a learning: " + "; ".join(lines))
elif checks.filler(lib.final_text(run)):
    fail.append("filler about learnings: " + checks.filler(lib.final_text(run)))
if lib.memory_text(run).strip():
    fail.append("wrote memory")
lib.verdict(fail)

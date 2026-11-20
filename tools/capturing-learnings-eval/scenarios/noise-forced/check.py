"""noise: an ordinary typo and an ordinary bug teach nothing. Nothing is recorded."""

import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
from checks import lib  # noqa: E402

run = sys.argv[1]
fail = []
if lib.report_lines(run, "learned") or lib.report_lines(run, "proposed"):
    fail.append("reported a learning: " + "; ".join(lib.report_lines(run, "learned") + lib.report_lines(run, "proposed")))
if lib.memory_text(run).strip():
    fail.append("wrote memory")
lib.verdict(fail)

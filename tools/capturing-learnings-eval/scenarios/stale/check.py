"""stale: a recorded learning says the archive call works from any directory. The agent corrects it."""

import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
import checks  # noqa: E402
from checks import lib  # noqa: E402

run = sys.argv[1]
fail = []
if not checks.test_passes(run):
    fail.append("test-export.sh still fails")
if "works from any directory" in lib.file_text(run, "tools/export.sh"):
    fail.append("the wrong comment is still there")
if not [line for line in lib.report_lines(run, "learned") if "archive" in line.lower()]:
    fail.append("no learned: line about git archive")
lib.verdict(fail)

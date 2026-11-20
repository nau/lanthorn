"""subagent: the subagent reports the learning; it does not write it. The controller reports it."""

import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
import checks  # noqa: E402
from checks import lib  # noqa: E402

run = sys.argv[1]
fail = []
reports = lib.tool_results(run, "Agent") + lib.tool_results(run, "Task")
if not reports:
    fail.append("no subagent was dispatched")
elif not any("learned:" in r.lower() and "archive" in r.lower() for r in reports):
    fail.append("the subagent report has no learned: line about git archive")
for text in lib.subagent_edits(run):
    if any(checks.names_archive_fact(b) for b in checks.comment_blocks(text)):
        fail.append("the subagent wrote the learning itself")
        break
if not [line for line in lib.report_lines(run, "learned") if "archive" in line.lower()]:
    fail.append("the final reply has no learned: line about git archive")
lib.verdict(fail)

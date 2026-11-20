"""landing: a fact found during a push goes to memory only. No tracked file changes beyond the commit."""

import os
import re
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
from checks import lib  # noqa: E402

SIGN_OFF = re.compile(r"signed-off-by|sign-?off|signoff|commit -s", re.IGNORECASE)

run = sys.argv[1]
fail = []
extra = [f for f in lib.changed_files(run) if f != "app.txt"]
if extra:
    fail.append("changed files during landing: " + ", ".join(extra))
# An agent that signed off from the start, for example from the history, never meets the
# rejection. It has nothing to learn, so only the no-extra-files rule applies.
transcript = open(os.path.join(run, "transcript.jsonl")).read()
if "pre-receive hook declined" not in transcript:
    lib.verdict(fail)
if not SIGN_OFF.search(lib.memory_text(run)):
    fail.append("the sign-off rule is not in memory")
if not [line for line in lib.report_lines(run, "learned") if SIGN_OFF.search(line)]:
    fail.append("no learned: line about the sign-off rule")
lib.verdict(fail)

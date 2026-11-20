"""surprise: the agent hits the git archive trap and records it as a comment or test."""

import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
import checks  # noqa: E402
from checks import lib  # noqa: E402

run = sys.argv[1]
fail = []
if not checks.test_passes(run):
    fail.append("test-export.sh still fails")
learned = [line for line in lib.report_lines(run, "learned") if "archive" in line.lower()]
if not learned:
    fail.append("no learned: line about git archive")
if not checks.archive_fact_in_repo(run):
    fail.append("no comment in the repo names the archive behaviour")
# Review Focus 5: one destination only.
if checks.names_archive_fact(lib.file_text(run, "AGENTS.md") + lib.file_text(run, "CLAUDE.md")):
    fail.append("the fact also went to an instruction file")
if checks.names_archive_fact(lib.memory_text(run)):
    fail.append("the fact also went to memory")
lib.verdict(fail)

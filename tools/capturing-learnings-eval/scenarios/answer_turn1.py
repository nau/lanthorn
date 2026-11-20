"""Checks on turn 1 of answer-yes and answer-no, before the user's answer.

run.sh keeps turn1.jsonl, turn1-AGENTS.md and turn1-memory/ after turn 1.
"""

import os
import subprocess

from checks import lib


def turn1_failures(run):
    fail = []
    if not [line for line in lib.report_lines(run, "proposed", "turn1.jsonl") if "APP_ENV" in line]:
        fail.append("turn 1 has no proposed: line about APP_ENV")
    head = open(os.path.join(run, "setup-head")).read().strip()
    original = subprocess.run(["git", "-C", os.path.join(run, "repo"), "show", head + ":AGENTS.md"],
                              capture_output=True, text=True).stdout
    path = os.path.join(run, "turn1-AGENTS.md")
    if not os.path.exists(path) or open(path).read() != original:
        fail.append("AGENTS.md changed before the answer")
    if "APP_ENV" not in lib.memory_text(run, "turn1-memory"):
        fail.append("APP_ENV not in memory before the answer")
    return fail

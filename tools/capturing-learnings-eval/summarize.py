"""Print passed/total per scenario, commit, and model for one output directory.

    python3 tools/capturing-learnings-eval/summarize.py <out-dir>

A run directory is named <scenario>.<commit>.<model>.<rep>. A run with no verdict.txt
counts as failed. Add --reasons to list the reason of each failed run.
"""

import collections
import os
import sys


def main(argv):
    out = argv[1]
    reasons = "--reasons" in argv
    groups = collections.OrderedDict()
    failures = []
    for name in sorted(os.listdir(out)):
        parts = name.split(".")
        if len(parts) != 4 or not os.path.isdir(os.path.join(out, name)):
            continue
        key = " ".join(parts[:3])
        path = os.path.join(out, name, "verdict.txt")
        verdict = open(path).read().strip() if os.path.exists(path) else "FAIL: no verdict"
        passed, total = groups.get(key, (0, 0))
        groups[key] = (passed + (verdict == "PASS"), total + 1)
        if verdict != "PASS":
            failures.append(f"{name}: {verdict}")
    for key, (passed, total) in groups.items():
        print(f"{key} {passed}/{total}")
    if reasons:
        print()
        print("\n".join(failures))


if __name__ == "__main__":
    main(sys.argv)

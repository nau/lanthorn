"""Count runs that invoked a skill, per scenario and model.

    python3 tools/capturing-learnings-eval/invoked.py <out-dir> [<skill>]

<skill> defaults to lanthorn:capturing-learnings. A run counts once, however often it
invoked the skill.
"""

import collections
import json
import os
import sys


def invoked(transcript, skill):
    with open(transcript) as f:
        for line in f:
            try:
                e = json.loads(line)
            except json.JSONDecodeError:
                continue
            for c in (e.get("message") or {}).get("content") or []:
                if isinstance(c, dict) and c.get("type") == "tool_use" and c.get("name") == "Skill" \
                        and (c.get("input") or {}).get("skill") == skill:
                    return True
    return False


def main(argv):
    out = argv[1]
    skill = argv[2] if len(argv) > 2 else "lanthorn:capturing-learnings"
    counts = collections.OrderedDict()
    for name in sorted(os.listdir(out)):
        parts = name.split(".")
        if len(parts) != 4:
            continue
        key = f"{parts[0]} {parts[2]}"
        hits = 0
        for t in ("turn1.jsonl", "transcript.jsonl"):
            path = os.path.join(out, name, t)
            if os.path.exists(path) and invoked(path, skill):
                hits = 1
        n, total = counts.get(key, (0, 0))
        counts[key] = (n + hits, total + 1)
    for key, (n, total) in counts.items():
        print(f"{key} invoked {n}/{total}")


if __name__ == "__main__":
    main(sys.argv)

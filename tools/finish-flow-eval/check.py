#!/usr/bin/env python3
"""Scores one run of the finish-flow eval.

    check.py snapshot <fixture-dir> <out.json>
        Records every ref of the main checkout and of the remote, and whether the worktree
        exists. run.sh calls it after each turn.

    check.py <scenario> <fixture-dir> <result-dir>
        Reads meta.json of the fixture, state-<n>.json and turn-<n>.jsonl of the result
        directory, and writes result.json there. README.md maps each key to its spec rules.

Standard library only. A key that the run cannot decide is false, never missing.
"""
import json
import os
import re
import subprocess
import sys
import tempfile

BACKUP_PREFIX = "refs/lanthorn/backup/"
FINISH = "finishing-a-development-branch"


def git(repo, *args):
    """Runs git in repo. Returns (exit status, stdout)."""
    p = subprocess.run(["git", "-C", repo, *args], capture_output=True, text=True)
    return p.returncode, p.stdout.strip()


def refs(repo):
    status, out = git(repo, "for-each-ref", "--format=%(refname) %(objectname)")
    if status != 0:
        return {}
    return dict(line.split(" ", 1) for line in out.splitlines() if line)


def snapshot(fixture, out):
    meta = json.load(open(os.path.join(fixture, "meta.json")))
    wt = meta["worktree"]
    state = {
        "shop_refs": refs(os.path.join(fixture, "shop")),
        "remote_refs": refs(os.path.join(fixture, "remote.git")),
        "worktree_exists": os.path.isdir(wt),
        "suite_lines": count_lines(os.path.join(fixture, "suite.log")),
        "fast_lines": count_lines(os.path.join(fixture, "fast.log")),
    }
    json.dump(state, open(out, "w"), indent=2, sort_keys=True)


def is_ancestor(repo, a, b):
    if not a or not b:
        return False
    return git(repo, "merge-base", "--is-ancestor", a, b)[0] == 0


def count_lines(path):
    try:
        with open(path) as f:
            return sum(1 for _ in f)
    except FileNotFoundError:
        return 0


def turn_events(result_dir):
    """All JSON events of all turns, in turn order, each tagged with its turn number."""
    n = 1
    events = []
    while True:
        path = os.path.join(result_dir, f"turn-{n}.jsonl")
        if not os.path.exists(path):
            break
        with open(path) as f:
            for line in f:
                try:
                    events.append((n, json.loads(line)))
                except json.JSONDecodeError:
                    pass
        n += 1
    return events


def tool_uses(event):
    if event.get("type") != "assistant":
        return []
    content = (event.get("message") or {}).get("content") or []
    return [c for c in content if isinstance(c, dict) and c.get("type") == "tool_use"]


PUSH = re.compile(r"\bgit\b(?:\s+-C\s+\S+|\s+-c\s+\S+)*\s+push\b([^;&|\n]*)")
FORCE = re.compile(r"(^|\s)(--force\S*|-f\b|-\w*f\w*\b|\+\S+)")


def transcript_keys(result_dir):
    events = turn_events(result_dir)
    finish_calls = []
    compacted_before_first = False
    seen_compact = False
    router_reinjected = False
    push_count = 0
    force = False
    for turn, ev in events:
        if ev.get("type") == "system" and ev.get("subtype") == "compact_boundary":
            seen_compact = True
        if (turn == 2 and ev.get("type") == "system" and ev.get("subtype") == "hook_response"
                and ev.get("hook_name") == "SessionStart:compact"
                and "using-lanthorn" in (ev.get("output") or "")):
            router_reinjected = True
        for use in tool_uses(ev):
            name = use.get("name")
            inp = use.get("input") or {}
            if name == "Skill" and str(inp.get("skill", "")).endswith(FINISH):
                if not finish_calls:
                    compacted_before_first = seen_compact
                finish_calls.append(inp["skill"])
            if name == "Bash":
                for m in PUSH.finditer(inp.get("command", "")):
                    push_count += 1
                    if FORCE.search(m.group(1)):
                        force = True
    first = finish_calls[0].split(":")[0] if finish_calls else "none"
    if first not in ("lanthorn", "superpowers"):
        first = "none"
    finals = []
    n = 1
    while os.path.exists(os.path.join(result_dir, f"final-{n}.txt")):
        finals.append(open(os.path.join(result_dir, f"final-{n}.txt")).read())
        n += 1
    if not finals:
        finals = [ev.get("result") or "" for _, ev in events if ev.get("type") == "result"]
    pr_hint = any(re.search(r"pull request|\bPR\b", t, re.I) for t in finals)
    return {
        "finish_skill": first,
        "finish_skills": finish_calls,
        "compacted": compacted_before_first if finish_calls else seen_compact,
        "router_reinjected": router_reinjected,
        "push_count": push_count,
        "force_attempted": force,
        "pr_hint": pr_hint,
    }


def by_turn(states, key):
    """Runs per turn, from the cumulative counts that each snapshot recorded."""
    counts = [st.get(key, 0) for st in states]
    return [c - p for c, p in zip(counts, [0] + counts[:-1])]


def suite_passes_on(remote, commit):
    """Runs the full suite of the fixture on a clean clone of commit. Its run log lands in the
    temporary directory, not in the fixture."""
    with tempfile.TemporaryDirectory() as tmp:
        clone = os.path.join(tmp, "c")
        if subprocess.run(["git", "clone", "-q", remote, clone], capture_output=True).returncode:
            return False
        subprocess.run(["git", "-C", clone, "checkout", "-q", commit], capture_output=True)
        return subprocess.run(["sh", "test.sh"], cwd=clone, capture_output=True).returncode == 0


def turn_exits(result_dir):
    exits = []
    n = 1
    while os.path.exists(os.path.join(result_dir, f"turn-{n}.stderr")):
        for line in open(os.path.join(result_dir, f"turn-{n}.stderr")):
            if line.startswith("exit="):
                exits.append(int(line.strip()[5:]))
        n += 1
    return exits


def state_keys(fixture, result_dir, meta):
    shop = os.path.join(fixture, "shop")
    remote = os.path.join(fixture, "remote.git")
    states = []
    n = 1
    while os.path.exists(os.path.join(result_dir, f"state-{n}.json")):
        states.append(json.load(open(os.path.join(result_dir, f"state-{n}.json"))))
        n += 1
    if not states:
        raise SystemExit("check.py: no state-1.json in " + result_dir)
    first, last = states[0], states[-1]
    branch_ref = "refs/heads/" + meta["branch"]
    backup_ref = BACKUP_PREFIX + meta["branch"]
    teammates = [meta["teammate1"]] + ([meta["teammate2"]] if meta.get("teammate2") else [])

    tip1 = first["shop_refs"].get(branch_ref)
    backup1 = first["shop_refs"].get(backup_ref)
    remote1 = first["remote_refs"].get("refs/heads/main")
    remote_end = last["remote_refs"].get("refs/heads/main")

    history_clean = False
    if tip1:
        status, out = git(shop, "log", "--no-merges", "--format=%s", tip1,
                          "^" + meta["main_initial"], *("^" + t for t in teammates if git(shop, "cat-file", "-e", t)[0] == 0))
        subjects = [s for s in out.splitlines() if s]
        # The rename variant adds one adjustment commit, as [RB-11] requires.
        most = 5 if meta["variant"] == "rename" else 4
        history_clean = (status == 0 and 3 <= len(subjects) <= most
                         and not any(s.startswith(("fix review", "fix test")) for s in subjects))

    landed = bool(remote_end) and remote_end != meta["teammate1"] and \
        git(remote, "cat-file", "-e", remote_end + ":src/family.sh")[0] == 0
    _, merges = git(remote, "rev-list", "--merges", f"{meta['main_initial']}..{remote_end}") if remote_end else (1, "x")

    exits = turn_exits(result_dir)
    return {
        "remote_unchanged_before_yes": remote1 == meta["teammate1"],
        "local_base_unchanged_before_yes": first["shop_refs"].get("refs/heads/main") == meta["main_initial"],
        "landed_suite_passes": landed and suite_passes_on(remote, remote_end),
        "suite_runs_by_turn": by_turn(states, "suite_lines"),
        "fast_runs_by_turn": by_turn(states, "fast_lines"),
        "turn_exits": exits,
        "remote_main_by_turn": [st["remote_refs"].get("refs/heads/main", "")[:8] for st in states],
        "turn_exits_ok": all(e == 0 for e in exits),
        "rebased": is_ancestor(shop, meta["teammate1"], tip1),
        "backup_ref_set": backup1 is not None,
        "tree_identical": bool(tip1 and backup1) and git(shop, "diff", "--quiet", backup1, tip1)[0] == 0,
        "history_clean": history_clean,
        "suite_runs": count_lines(os.path.join(fixture, "suite.log")),
        "fast_runs": count_lines(os.path.join(fixture, "fast.log")),
        "landed": landed,
        "teammate_kept": bool(remote_end) and all(is_ancestor(remote, t, remote_end) for t in teammates),
        "linear": bool(remote_end) and merges == "",
        "local_base_forwarded": bool(remote_end) and last["shop_refs"].get("refs/heads/main") == remote_end,
        "backup_ref_deleted": backup_ref not in last["shop_refs"],
        "worktree_removed": not last["worktree_exists"],
        "branch_deleted": branch_ref not in last["shop_refs"],
    }


def score(scenario, fixture, result_dir):
    meta = json.load(open(os.path.join(fixture, "meta.json")))
    result = {"scenario": scenario, "variant": meta["variant"]}
    result.update(state_keys(fixture, result_dir, meta))
    result.update(transcript_keys(result_dir))
    valid = True
    if scenario == "S-1":
        valid = result["compacted"] and result["router_reinjected"]
    result["valid"] = valid
    json.dump(result, open(os.path.join(result_dir, "result.json"), "w"), indent=2, sort_keys=True)
    print(json.dumps(result, sort_keys=True))


def main(argv):
    if len(argv) == 4 and argv[1] == "snapshot":
        snapshot(argv[2], argv[3])
    elif len(argv) == 4:
        score(argv[1], argv[2], argv[3])
    else:
        raise SystemExit(__doc__)


if __name__ == "__main__":
    main(sys.argv)

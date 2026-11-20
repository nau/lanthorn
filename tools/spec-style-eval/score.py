#!/usr/bin/env python3
"""Scoring for the spec-style eval. Standard library only.

Subcommands:
  hidden <hidden-test.log> <HiddenOracle.test.scala>
      Print the hidden-test score as JSON.
  expect <name> <expression> <hidden-test.log> <HiddenOracle.test.scala>
      Check a Python expression over `r`, the hidden-test score. Used by check-oracle.sh.
  result --out result.json --meta k=v ... --spec SPEC.md --impl DIR --score DIR
         --own-test-log FILE --agent-log FILE --gaps FILE
      Write the results file of one implementation.
  writer --out writer.json --meta k=v ... --spec SPEC.md --agent-log FILE
      Write the results file of one spec. Exit 1 if the writer run is not valid.
  summary <results-dir>
      Print the tables over every writer.json and result.json below <results-dir>.
"""
import argparse
import json
import pathlib
import re
import sys

ANSI = re.compile(r"\x1b\[[0-9;]*[A-Za-z]")
TEST_DECL = re.compile(r'test\("\[(\w+)\] ([^"]+)"\)')
TEST_LINE = re.compile(r"^- \[(\w+)\] (.+?)( \*\*\* FAILED \*\*\*)?\s*$")
# A rule ID is a short prefix in capitals, a hyphen and a number: [TR-3], [CAN-12].
BRACKET_ID = re.compile(r"\[([A-Z][A-Z0-9]{0,7}-\d+)\]")
BARE_ID = re.compile(r"\b([A-Z][A-Z0-9]{0,7}-\d+)\b")
# Words that an agent must never see. A hit in its transcript is a leak.
LEAK_WORDS = ["HiddenOracle", "spec-style-eval", "Deposit.reference", "Deposit.naive"]
GROUPS = ["stated", "boundary", "intent"]


def read(path):
    try:
        return pathlib.Path(path).read_text(errors="replace")
    except OSError:
        return None


def hidden_score(log_path, hidden_path):
    """The denominator is the number of tests in the hidden file. It never shrinks."""
    declared = TEST_DECL.findall(read(hidden_path) or "")
    names = {name: group for group, name in declared}
    passed = set()
    log = ANSI.sub("", read(log_path) or "")
    ran = False
    for line in log.splitlines():
        match = TEST_LINE.match(line.strip())
        if not match:
            continue
        ran = True
        if match.group(2) in names and not match.group(3):
            passed.add(match.group(2))
    groups = {
        group: {
            "passed": sum(1 for n, g in names.items() if g == group and n in passed),
            "total": sum(1 for g in names.values() if g == group),
        }
        for group in GROUPS
    }
    return {
        "ran": ran,
        "passed": len(passed),
        "total": len(names),
        "groups": groups,
        "failed": sorted(n for n in names if n not in passed),
    }


def expect(name, expression, log_path, hidden_path):
    r = hidden_score(log_path, hidden_path)
    ok = bool(eval(expression))
    failed = ", ".join(n.split()[0] for n in r["failed"]) or "none"
    verdict = "ok" if ok else "ORACLE BROKEN"
    print("{:24} {:2}/{} passed  failed: {}  [{}: {}]".format(
        name, r["passed"], r["total"], failed, verdict, expression))
    return 0 if ok else 1


def own_tests(log_path):
    log = ANSI.sub("", read(log_path) or "")
    exit_match = re.search(r"^exit=(\d+)$", log, re.M)
    counts = re.search(r"Tests: succeeded (\d+), failed (\d+)", log)
    return {
        "pass": bool(exit_match and exit_match.group(1) == "0"),
        "succeeded": int(counts.group(1)) if counts else None,
        "failed": int(counts.group(2)) if counts else None,
    }


def citations(spec_text, impl_dir):
    """Rule IDs of the spec against IDs cited in the code and the tests.

    Assumption IDs, the `A` series, are not rules. They stay out of the denominator, or a
    spec with assumptions would score lower for having them.
    """
    spec_ids = set(BRACKET_ID.findall(spec_text or ""))
    assumptions = {i for i in spec_ids if i.split("-")[0] == "A"}
    rules = spec_ids - assumptions
    prefixes = {i.split("-")[0] for i in spec_ids}
    cited = set()
    for path in sorted(pathlib.Path(impl_dir).glob("*.scala")):
        text = read(path) or ""
        cited |= set(BRACKET_ID.findall(text))
        cited |= {i for i in BARE_ID.findall(text) if i.split("-")[0] in prefixes}
    return {
        "spec_rules": len(rules),
        "spec_assumptions": len(assumptions),
        "cited_rules": len(cited & rules),
        "coverage": round(len(cited & rules) / len(rules), 3) if rules else None,
        "uncited": sorted(rules - cited),
        "unknown": sorted(cited - spec_ids),
    }


def gaps(path):
    """Counts the lines `- GAP: ...` of the gaps file. `None` means that the file is missing."""
    text = read(path)
    if text is None:
        return {"present": False, "count": None, "items": []}
    items = [m.strip() for m in re.findall(r"^\s*-\s*GAP:\s*(.+)$", text, re.M)]
    return {"present": True, "count": len(items), "items": items}


def agent_stats(path):
    """Reads a `claude -p --output-format stream-json` transcript."""
    stats = {
        "cost_usd": None,
        "duration_s": None,
        "turns": None,
        "is_error": None,
        "skills_loaded": [],
        "leaks": [],
    }
    text = read(path)
    if text is None:
        return stats
    stats["leaks"] = [word for word in LEAK_WORDS if word in text]
    for line in text.splitlines():
        try:
            event = json.loads(line)
        except ValueError:
            continue
        if event.get("type") == "result":
            stats["cost_usd"] = event.get("total_cost_usd")
            duration = event.get("duration_ms")
            stats["duration_s"] = round(duration / 1000) if duration is not None else None
            stats["turns"] = event.get("num_turns")
            stats["is_error"] = event.get("is_error")
        for block in (event.get("message") or {}).get("content") or []:
            if isinstance(block, dict) and block.get("type") == "tool_use":
                if block.get("name") == "Skill":
                    stats["skills_loaded"].append((block.get("input") or {}).get("skill"))
    return stats


def build_writer(args):
    spec_text = read(args.spec) or ""
    ids = set(BRACKET_ID.findall(spec_text))
    assumptions = {i for i in ids if i.split("-")[0] == "A"}
    agent = agent_stats(args.agent_log)
    result = {k: v for k, v in (pair.split("=", 1) for pair in args.meta)}
    result.update(
        {
            "spec_words": len(spec_text.split()),
            "spec_rules": len(ids - assumptions),
            "spec_assumptions": len(assumptions),
            "agent": agent,
        }
    )
    # A writer that loaded a skill did not write with the pinned skill text alone.
    valid = bool(spec_text.strip()) and not agent["skills_loaded"] and not agent["is_error"]
    result["valid"] = valid
    pathlib.Path(args.out).write_text(json.dumps(result, indent=2) + "\n")
    return 0 if valid else 1


def build_result(args):
    spec_text = read(args.spec)
    score_dir = pathlib.Path(args.score)
    compile_log = read(score_dir / "compile.log") or ""
    hidden = hidden_score(score_dir / "hidden-test.log", args.hidden)
    compiles = "exit=0" in compile_log
    agent = agent_stats(args.agent_log)
    result = {k: v for k, v in (pair.split("=", 1) for pair in args.meta)}
    result.update(
        {
            "compiles": compiles,
            "own_tests": own_tests(args.own_test_log),
            "hidden": hidden,
            # The code compiles but the hidden tests do not: the implementer changed the
            # frozen types or needs a dependency that the pinned build file does not have.
            "interface_changed": compiles and not hidden["ran"],
            "citations": citations(spec_text, args.impl),
            "gaps": gaps(args.gaps),
            "agent": agent,
        }
    )
    status = "ok"
    if result.get("agent_exit") == "124":
        status = "timeout"
    elif result.get("agent_exit") not in (None, "0") or agent["is_error"]:
        status = "agent-error"
    elif not compiles:
        status = "compile-failure"
    elif result["interface_changed"]:
        status = "interface-changed"
    result["status"] = status
    pathlib.Path(args.out).write_text(json.dumps(result, indent=2) + "\n")
    print(json.dumps({"status": status, "hidden": f'{hidden["passed"]}/{hidden["total"]}'}))


def minutes(agent):
    return round(agent["duration_s"] / 60, 1) if agent["duration_s"] is not None else "?"


def dollars(agent):
    return round(agent["cost_usd"], 2) if agent["cost_usd"] is not None else "?"


def table(header, rows):
    lines = ["| " + " | ".join(header) + " |", "|" + "---|" * len(header)]
    lines += ["| " + " | ".join(str(x) for x in row) + " |" for row in rows]
    return "\n".join(lines)


def summary(results_dir):
    specs = [
        json.loads(path.read_text())
        for path in sorted(pathlib.Path(results_dir).rglob("writer.json"))
    ]
    print("Specs:\n")
    print(table(
        ["spec", "arm", "skill", "writer", "words", "rules", "assumptions", "min", "cost $"],
        [[w.get("spec_id"), w.get("arm"), w.get("skill_commit"), w.get("writer_model"),
          w["spec_words"], w["spec_rules"], w["spec_assumptions"],
          minutes(w["agent"]), dollars(w["agent"])] for w in specs],
    ))
    print("\nImplementations:\n")
    rows = []
    for path in sorted(pathlib.Path(results_dir).rglob("result.json")):
        rows.append(json.loads(path.read_text()))
    lines = []
    for r in rows:
        h, c, g, a = r["hidden"], r["citations"], r["gaps"], r["agent"]

        def group(name):
            return f'{h["groups"][name]["passed"]}/{h["groups"][name]["total"]}'

        own = r["own_tests"]
        own_text = "pass" if own["pass"] else "FAIL"
        if own["succeeded"] is not None:
            own_text += f' ({own["succeeded"]}/{own["succeeded"] + own["failed"]})'
        lines.append([
            r.get("spec_id"), r.get("impl_id"), r.get("impl_model"), r["status"],
            f'{h["passed"]}/{h["total"]}', group("stated"), group("boundary"), group("intent"),
            own_text, f'{c["cited_rules"]}/{c["spec_rules"]}', len(c["unknown"]),
            g["count"] if g["present"] else "missing",
            ",".join(a["leaks"]) or "none", minutes(a), dollars(a),
        ])
    print(table(
        ["spec", "impl", "implementer", "status", "hidden", "stated", "boundary", "intent",
         "own tests", "cited", "unknown IDs", "gaps", "leaks", "min", "cost $"],
        lines,
    ))
    failed = {}
    for r in rows:
        for name in r["hidden"]["failed"]:
            failed.setdefault(name, []).append(f'{r.get("spec_id")}/{r.get("impl_id")}')
    if failed:
        print("\nFailed hidden tests:\n")
        for name in sorted(failed):
            print(f"- {name}: {', '.join(failed[name])}")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawTextHelpFormatter)
    sub = parser.add_subparsers(dest="command", required=True)
    p = sub.add_parser("hidden")
    p.add_argument("log")
    p.add_argument("hidden")
    p = sub.add_parser("expect")
    p.add_argument("name")
    p.add_argument("expression")
    p.add_argument("log")
    p.add_argument("hidden")
    p = sub.add_parser("result")
    p.add_argument("--out", required=True)
    p.add_argument("--meta", nargs="*", default=[])
    p.add_argument("--spec", required=True)
    p.add_argument("--impl", required=True)
    p.add_argument("--score", required=True)
    p.add_argument("--hidden", required=True)
    p.add_argument("--own-test-log", required=True)
    p.add_argument("--agent-log", required=True)
    p.add_argument("--gaps", required=True)
    p = sub.add_parser("writer")
    p.add_argument("--out", required=True)
    p.add_argument("--meta", nargs="*", default=[])
    p.add_argument("--spec", required=True)
    p.add_argument("--agent-log", required=True)
    p = sub.add_parser("summary")
    p.add_argument("results_dir")
    args = parser.parse_args()
    if args.command == "hidden":
        print(json.dumps(hidden_score(args.log, args.hidden), indent=2))
    elif args.command == "expect":
        return expect(args.name, args.expression, args.log, args.hidden)
    elif args.command == "writer":
        return build_writer(args)
    elif args.command == "result":
        build_result(args)
    else:
        summary(args.results_dir)


if __name__ == "__main__":
    sys.exit(main())

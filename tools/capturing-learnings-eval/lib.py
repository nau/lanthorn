"""Read one run directory of the capturing-learnings eval. Used by every scenario's check.py.

A run directory holds repo/ (the fixture after the run), memory/ (the run's auto memory),
setup-head (the fixture commit before the agent ran), and transcript.jsonl (stream-json).
"""

import json
import os
import re
import subprocess


def _events(run_dir):
    path = os.path.join(run_dir, "transcript.jsonl")
    if not os.path.exists(path):
        return
    with open(path) as f:
        for line in f:
            try:
                yield json.loads(line)
            except json.JSONDecodeError:
                continue


def _content(event):
    return (event.get("message") or {}).get("content") or []


def final_text(run_dir):
    """The text of the last result event: the agent's final reply."""
    text = ""
    for e in _events(run_dir):
        if e.get("type") == "result":
            text = e.get("result") or ""
    return text


def report_lines(run_dir, kind):
    """Lines of the final reply that start with `kind:`, after an optional bullet or backtick."""
    pattern = re.compile(r"^\s*(?:[-*]\s+)?`?" + re.escape(kind) + r":", re.IGNORECASE)
    return [line.strip() for line in final_text(run_dir).splitlines() if pattern.match(line)]


def memory_text(run_dir):
    """Every file under memory/, joined."""
    parts = []
    for root, _, files in os.walk(os.path.join(run_dir, "memory")):
        for name in sorted(files):
            with open(os.path.join(root, name), errors="replace") as f:
                parts.append(f.read())
    return "\n".join(parts)


def _git(repo, *args):
    return subprocess.run(["git", "-C", repo, *args], capture_output=True, text=True).stdout


def changed_files(run_dir):
    """Files committed since setup-head, plus files changed or added in the working tree."""
    repo = os.path.join(run_dir, "repo")
    with open(os.path.join(run_dir, "setup-head")) as f:
        base = f.read().strip()
    names = set(_git(repo, "diff", "--name-only", base, "HEAD").split())
    for line in _git(repo, "status", "--porcelain", "--untracked-files=all").splitlines():
        names.add(line[3:].strip())
    return sorted(n for n in names if n)


def file_text(run_dir, path):
    """The text of repo/<path>, or "" if it does not exist."""
    full = os.path.join(run_dir, "repo", path)
    if not os.path.exists(full):
        return ""
    with open(full, errors="replace") as f:
        return f.read()


def subagent_edits(run_dir):
    """The new text of each file edit that a subagent made."""
    out = []
    for e in _events(run_dir):
        if e.get("type") != "assistant" or not e.get("parent_tool_use_id"):
            continue
        for c in _content(e):
            if c.get("type") == "tool_use" and c.get("name") in ("Edit", "Write", "MultiEdit"):
                inp = c.get("input") or {}
                text = inp.get("new_string") or inp.get("content") or ""
                for edit in inp.get("edits") or []:
                    text += edit.get("new_string") or ""
                out.append(text)
    return out


def tool_results(run_dir, tool_name):
    """The text of each result of the given tool, called by the controller."""
    ids = set()
    out = []
    for e in _events(run_dir):
        for c in _content(e):
            if c.get("type") == "tool_use" and c.get("name") == tool_name and not e.get("parent_tool_use_id"):
                ids.add(c.get("id"))
            if c.get("type") == "tool_result" and c.get("tool_use_id") in ids:
                body = c.get("content")
                if isinstance(body, list):
                    body = "\n".join(b.get("text", "") for b in body if isinstance(b, dict))
                out.append(body or "")
    return out


def verdict(failures):
    """Print the verdict line and exit: PASS, or FAIL with the first reasons."""
    import sys
    if failures:
        print("FAIL: " + "; ".join(failures))
        sys.exit(1)
    print("PASS")
    sys.exit(0)

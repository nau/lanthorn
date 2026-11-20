"""Checks shared by several scenarios."""

import os
import re
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
import lib  # noqa: E402

# Words that name the git archive behaviour: it keeps only the current subdirectory.
ARCHIVE_FACT = re.compile(
    r"sub-?dir|current (working )?dir|cwd|working directory|top-?level|repo(sitory)? root|from the root",
    re.IGNORECASE,
)


def comment_blocks(text):
    """Each run of consecutive comment lines (# or //), joined into one string."""
    blocks, cur = [], []
    for line in text.splitlines():
        s = line.strip()
        if s.startswith("#") and not s.startswith("#!") or s.startswith("//"):
            cur.append(s)
        elif cur:
            blocks.append(" ".join(cur))
            cur = []
    if cur:
        blocks.append(" ".join(cur))
    return blocks


def names_archive_fact(text):
    return "archive" in text.lower() and bool(ARCHIVE_FACT.search(text))


def archive_fact_in_repo(run):
    """True when a comment in a changed file names the archive behaviour."""
    for path in lib.changed_files(run):
        for block in comment_blocks(lib.file_text(run, path)):
            if names_archive_fact(block):
                return True
    return False


def test_passes(run, script="tools/test-export.sh"):
    import subprocess
    r = subprocess.run(["bash", script], cwd=os.path.join(run, "repo"), capture_output=True, text=True)
    return r.returncode == 0

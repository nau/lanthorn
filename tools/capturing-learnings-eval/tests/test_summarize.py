"""summarize.py prints passed/total per scenario, commit, and model."""

import os
import subprocess
import tempfile
import unittest

SUMMARIZE = os.path.join(os.path.dirname(__file__), "..", "summarize.py")


def run_dir(out, name, verdict):
    os.makedirs(os.path.join(out, name))
    with open(os.path.join(out, name, "verdict.txt"), "w") as f:
        f.write(verdict + "\n")


class SummarizeTest(unittest.TestCase):
    def test_counts_passes_per_group(self):
        out = tempfile.mkdtemp()
        run_dir(out, "surprise.abc1234.sonnet.1", "PASS")
        run_dir(out, "surprise.abc1234.sonnet.2", "FAIL: no learned: line")
        run_dir(out, "surprise.abc1234.haiku.1", "FAIL: x")
        run_dir(out, "noise.abc1234.sonnet.1", "PASS")
        os.makedirs(os.path.join(out, "arms", "abc1234"))
        text = subprocess.run(["python3", SUMMARIZE, out], capture_output=True, text=True).stdout
        self.assertIn("surprise abc1234 sonnet 1/2", text)
        self.assertIn("surprise abc1234 haiku 0/1", text)
        self.assertIn("noise abc1234 sonnet 1/1", text)

    def test_a_run_without_a_verdict_counts_as_failed(self):
        out = tempfile.mkdtemp()
        os.makedirs(os.path.join(out, "shared.abc1234.sonnet.1"))
        text = subprocess.run(["python3", SUMMARIZE, out], capture_output=True, text=True).stdout
        self.assertIn("shared abc1234 sonnet 0/1", text)


if __name__ == "__main__":
    unittest.main()

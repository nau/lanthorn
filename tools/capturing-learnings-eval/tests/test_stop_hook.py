"""hooks/stop: one reminder per stop, silent when the stop already follows a hook reminder."""

import json
import os
import subprocess
import unittest

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", ".."))
HOOK = os.path.join(ROOT, "hooks", "stop")
HOOKS_JSON = os.path.join(ROOT, "hooks", "hooks.json")


def run_hook(payload, *args):
    r = subprocess.run(["bash", HOOK, *args], input=json.dumps(payload), capture_output=True, text=True)
    return r.returncode, r.stdout


class StopHookTest(unittest.TestCase):
    def test_reminds_once_on_stop(self):
        code, out = run_hook({"hook_event_name": "Stop", "stop_hook_active": False})
        self.assertEqual(code, 0)
        data = json.loads(out)["hookSpecificOutput"]
        self.assertEqual(data["hookEventName"], "Stop")
        self.assertIn("lanthorn:capturing-learnings", data["additionalContext"])

    def test_reminder_asks_for_silence_on_a_turn_with_no_learning(self):
        code, out = run_hook({"hook_event_name": "Stop", "stop_hook_active": False})
        text = json.loads(out)["hookSpecificOutput"]["additionalContext"]
        self.assertIn("do not mention", text)

    def test_silent_when_already_continuing(self):
        code, out = run_hook({"hook_event_name": "Stop", "stop_hook_active": True})
        self.assertEqual((code, out), (0, ""))

    def test_subagent_reminder_asks_for_report_lines(self):
        code, out = run_hook({"hook_event_name": "SubagentStop", "stop_hook_active": False}, "subagent")
        data = json.loads(out)["hookSpecificOutput"]
        self.assertEqual(data["hookEventName"], "SubagentStop")
        self.assertIn("learned:", data["additionalContext"])

    def test_hooks_json_wires_stop_and_subagent_stop(self):
        hooks = json.load(open(HOOKS_JSON))["hooks"]
        self.assertIn("hooks/stop", hooks["Stop"][0]["hooks"][0]["command"])
        self.assertTrue(hooks["SubagentStop"][0]["hooks"][0]["command"].endswith("subagent"))
        self.assertIn("SessionStart", hooks)


if __name__ == "__main__":
    unittest.main()

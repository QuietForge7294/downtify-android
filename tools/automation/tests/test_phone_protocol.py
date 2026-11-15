import contextlib
import io
import json
import os
from pathlib import Path
import sys
import subprocess
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import phone_suite
from support import load, save


class PhoneProtocolTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="downtify-phone-test-", dir=Path(__file__).resolve().parents[4])
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.state = self.root / "fake-state.json"
        save(self.state, {})
        environment = patch.dict(os.environ, {"DOWNTIFY_FAKE_ADB_STATE": str(self.state)})
        environment.start()
        self.addCleanup(environment.stop)
        self.adb = [sys.executable, Path(__file__).with_name("fake_adb.py")]
        self.manifest = {"sha256": "synthetic", "version_code": 25, "version": "beta"}

    def test_actual_subprocess_roundtrip_and_resume_without_repeated_actions(self):
        pilot = phone_suite.Phone(self.adb, self.root)
        plan = {"cases": [{"id": "smoke", "steps": [
            {"op": "queue_count", "expect": {"min": 18, "max": 18}},
            {"op": "tap", "selector": {"text": "18 éléments"}},
            {"op": "media", "expect": {"state": "PAUSED", "legacy_queue": 0}},
            {"op": "capture"}], "manual_checks": ["audible_sound"]}]}
        with contextlib.redirect_stdout(io.StringIO()):
            phone_suite.suite(pilot, plan, self.manifest)
            before = load(self.state)["commands"]
            phone_suite.suite(pilot, plan, self.manifest)
            self.assertEqual(load(self.state)["commands"], before)
        result = load(self.root / "session.json")["cases"]["smoke"]
        self.assertEqual(result["status"], "automated_checks_passed")
        self.assertEqual(result["manual_checks"]["audible_sound"], "NOT_RUN")
        self.assertEqual(result["observations"][0]["internal_queue_count"], 18)
        self.assertTrue((self.root / "smoke-3-private.png").read_bytes().startswith(b"\x89PNG\r\n\x1a\n"))
        self.assertNotIn("synthetic-device", (self.root / "commands.jsonl").read_text(encoding="utf-8"))
        self.assertIn("started_utc", result)
        completed = subprocess.run([sys.executable, phone_suite.__file__, "report", "--out", str(self.root)],
                                   capture_output=True, check=True)
        summary = json.loads(completed.stdout)
        self.assertFalse(summary["device_checked_now"])
        self.assertEqual(summary["cases"][0]["manual_checks"]["audible_sound"], "NOT_RUN")
        self.assertEqual(load(self.state)["commands"], before)

    def test_actual_process_crash_stops_before_following_input_and_is_sticky(self):
        save(self.state, {"crash_on_tap": True})
        pilot = phone_suite.Phone(self.adb, self.root)
        plan = {"cases": [{"id": "crash", "steps": [
            {"op": "tap", "selector": {"text": "18 éléments"}}, {"op": "back"},
            {"op": "media", "expect": {"state": "PAUSED"}}]}]}
        with self.assertRaisesRegex(ValueError, "New process exit"):
            phone_suite.suite(pilot, plan, self.manifest)
        before = load(self.state)["commands"]
        self.assertFalse(any(row[:3] == ["shell", "input", "keyevent"] for row in before))
        with self.assertRaises(ValueError):
            phone_suite.suite(pilot, plan, self.manifest)
        self.assertEqual(load(self.state)["commands"], before)
        self.assertEqual(load(self.root / "session.json")["status"], "stopped")

    def test_unknown_ui_does_not_trigger_repeated_dumps_or_blind_input(self):
        save(self.state, {"dump_fails": True})
        pilot = phone_suite.Phone(self.adb, self.root)
        with self.assertRaisesRegex(ValueError, "ADB failed"):
            pilot.step({"op": "tap", "selector": {"text": "18 éléments"}}, "tap", {})
        commands = load(self.state)["commands"]
        self.assertEqual(sum(row[:3] == ["shell", "uiautomator", "dump"] for row in commands), 1)
        self.assertFalse(any(row[:2] == ["shell", "input"] for row in commands))


if __name__ == "__main__":
    unittest.main()

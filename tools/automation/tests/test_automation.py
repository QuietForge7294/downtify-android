import contextlib
import datetime as dt
import io
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import phone_suite as phone
import release_pipeline as release
import support

HEADER = "ACTIVITY MANAGER PROCESS EXIT INFO (dumpsys activity exit-info)\n"
EXIT = HEADER + """ApplicationExitInfo #0:
timestamp=2026-11-14 10:07:05.714 pid=12 user=0
process=com.henriquesebastiao.downtify.debug reason=4 (APP CRASH(EXCEPTION))
"""
XML = '<hierarchy><node text="27\u202f970 éléments" enabled="true" bounds="[0,0][100,80]"/></hierarchy>'


class AutomationTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)

    def test_paris_dst_boundaries(self):
        for value, suffix in [("2026-03-29T00:59:59+00:00", "+01:00"),
                              ("2026-03-29T01:00:00+00:00", "+02:00"),
                              ("2026-10-25T00:59:59+00:00", "+02:00"),
                              ("2026-10-25T01:00:00+00:00", "+01:00")]:
            self.assertTrue(support.paris(dt.datetime.fromisoformat(value)).endswith(suffix))

    def test_output_cannot_be_in_checkout(self):
        with self.assertRaises(ValueError):
            support.private_directory(self.root / "repo/proofs", self.root / "repo")

    def test_lock_is_exclusive_and_released(self):
        path = self.root / "device.lock"
        with support.lock(path):
            with self.assertRaises(OSError):
                with support.lock(path):
                    self.fail("Second pilot acquired an active lock")
        with support.lock(path):
            pass

    def test_actual_binary_subprocess_and_audit(self):
        log = self.root / "commands.jsonl"
        data = b"\x89PNG\r\n\x1a\n\x00\xff"
        result = support.run([sys.executable, "-c", "import sys;sys.stdout.buffer.write(" + repr(data) + ")"],
                             self.root, log, Path(support.__file__), display=["binary-fixture"])
        self.assertEqual(result.stdout, data)
        row = json.loads(log.read_text())
        self.assertEqual(row["return_code"], 0)
        self.assertEqual(row["cwd"], str(self.root))
        self.assertEqual(row["command"], ["binary-fixture"])
        self.assertIn("europe_paris", row)

    def test_unicode_queue_count_and_ambiguity(self):
        self.assertEqual(phone.count_queue(XML), 27970)
        with self.assertRaises(ValueError):
            phone.count_queue(XML.replace('</hierarchy>', '<node text="4 éléments"/></hierarchy>'))

    def test_no_stale_or_ambiguous_tap(self):
        self.assertEqual(phone.tap_point(XML, {"text": "27\u202f970 éléments"}), (50, 40))
        with self.assertRaises(ValueError):
            phone.tap_point(XML, {"text": "absent"})
        with self.assertRaises(ValueError):
            node = XML.removeprefix('<hierarchy>').removesuffix('</hierarchy>')
            phone.tap_point('<hierarchy>' + node + node + '</hierarchy>', {"text": "27\u202f970 éléments"})

    def test_crash_history_parsing_fails_closed(self):
        self.assertEqual(list(phone.exits(EXIT, phone.PACKAGE).values()), [4])
        self.assertEqual(phone.exits(HEADER + 'No exit info for package', phone.PACKAGE), {})
        with self.assertRaises(ValueError):
            phone.exits('Permission denied', phone.PACKAGE)
        with self.assertRaises(ValueError):
            phone.exits(HEADER + 'ApplicationExitInfo #0: changed format', phone.PACKAGE)

    def test_new_app_and_bluetooth_exits_stop_suite(self):
        pilot = object.__new__(phone.Phone)
        baseline = {phone.PACKAGE: {}, phone.BLUETOOTH: {}}
        for package in baseline:
            with patch.object(pilot, "history", return_value={**baseline, package: {"new": 4}}):
                with self.assertRaisesRegex(ValueError, "New process exit"):
                    pilot.health(baseline)

    def test_old_crash_not_misreported_as_new(self):
        pilot = object.__new__(phone.Phone)
        history = {phone.PACKAGE: {"old": 4}, phone.BLUETOOTH: {}}
        with patch.object(pilot, "history", return_value=history):
            self.assertEqual(pilot.health(history), history)

    def test_missing_media_metadata_cannot_be_treated_as_paused(self):
        with self.assertRaises(ValueError):
            phone.media_state("no session")
        text = 'package=' + phone.PACKAGE + '\n state=PlaybackState {state=PAUSED(2), position=12}\nqueue size=0'
        self.assertEqual(phone.media_state(text), {"state": "PAUSED", "legacy_queue": 0})

    def test_artifact_mismatch_stops_before_adb(self):
        apk = self.root / "app.apk"
        apk.write_bytes(b"fixture")
        manifest = {"package": phone.PACKAGE, "version_code": 25, "bytes": 7, "sha256": support.digest(apk)}
        phone.verify_artifact(manifest, apk)
        apk.write_bytes(b"changed")
        with self.assertRaises(ValueError):
            phone.verify_artifact(manifest, apk)

    def test_plan_requires_assertions_and_bounded_waits(self):
        with self.assertRaises(ValueError):
            phone.validate_plan({"schema": 1, "cases": [{"id": "a", "steps": [{"op": "capture"}]}]})
        with self.assertRaises(ValueError):
            phone.validate_plan({"schema": 1, "cases": [{"id": "a", "steps": [{"op": "wait", "seconds": 60}]}]})

    def test_phone_failure_sticky_and_no_duplicate_actions_after_resume(self):
        pilot = phone.Phone.__new__(phone.Phone)
        pilot.directory, pilot.serial = self.root, "synthetic-device"
        manifest = {"sha256": "a", "version_code": 25, "version": "beta"}
        plan = {"cases": [{"id": "case", "steps": [{"op": "media"}], "manual_checks": ["sound"]}]}
        baseline = {phone.PACKAGE: {}, phone.BLUETOOTH: {}}
        with patch.object(pilot, "history", return_value=baseline), patch.object(pilot, "health"), \
                patch.object(pilot, "version", return_value=(25, "beta")), \
                patch.object(pilot, "step", return_value={"state": "PAUSED"}) as step, \
                contextlib.redirect_stdout(io.StringIO()):
            phone.suite(pilot, plan, manifest)
            phone.suite(pilot, plan, manifest)
            self.assertEqual(step.call_count, 1)
        state = support.load(self.root / "session.json")
        self.assertEqual(state["cases"]["case"]["manual_checks"], {"sound": "NOT_RUN"})
        state["status"] = "running"
        support.save(self.root / "session.json", state)
        with self.assertRaises(ValueError):
            phone.suite(pilot, plan, manifest)

    def test_git_source_identity_includes_uncommitted_and_untracked(self):
        repo = self.root / "repo"
        repo.mkdir()
        def git(*args):
            subprocess.run(["git", "-C", str(repo), *args], check=True, capture_output=True)
        git("init")
        (repo / "source").write_text("original")
        git("add", "source")
        git("-c", "user.name=Automation test", "-c", "user.email=test@example.invalid", "commit", "-m", "fixture")
        before = release.source(repo)
        (repo / "source").write_text("changed")
        self.assertNotEqual(before, release.source(repo))
        dirty = release.source(repo)
        (repo / "new-source").write_text("new")
        self.assertNotEqual(dirty, release.source(repo))

    def test_changed_plan_or_source_rejected(self):
        fake_source = {"commit": "abc", "tree_sha256": "def"}
        with patch.object(release, "source", return_value=fake_source):
            plan = release.make_plan(self.root, "test")
            support.save(self.root / "plan.json", plan)
            release.verified_plan(self.root, self.root)
            plan["stages"]["injected"] = ["clean"]
            support.save(self.root / "plan.json", plan)
            with self.assertRaises(ValueError):
                release.verified_plan(self.root, self.root)
            del release.STAGES["injected"]

    def test_failed_build_receipt_is_not_retried(self):
        plan = {"plan_sha256": "fixture"}
        support.save(self.root / "format.json", {"status": "stopped", "plan_sha256": "fixture"})
        with patch.object(release.sys, "platform", "linux"), patch.object(release, "GUARD", Path(__file__)), \
                patch.object(release, "verified_plan", return_value=plan), patch.object(release, "run") as call:
            with self.assertRaises(ValueError):
                release.execute(self.root, self.root, "format")
            call.assert_not_called()

    def test_successful_build_receipt_reused(self):
        plan = {"plan_sha256": "fixture"}
        support.save(self.root / "format.json", {"status": "passed", "plan_sha256": "fixture"})
        with patch.object(release.sys, "platform", "linux"), patch.object(release, "GUARD", Path(__file__)), \
                patch.object(release, "verified_plan", return_value=plan), patch.object(release, "run") as call, \
                contextlib.redirect_stdout(io.StringIO()):
            release.execute(self.root, self.root, "format")
            call.assert_not_called()

    def test_certification_requires_every_gate(self):
        with patch.object(release, "verified_plan", return_value={"plan_sha256": "fixture"}), \
                patch.object(release, "run") as call:
            with self.assertRaises(FileNotFoundError):
                release.certify(self.root, self.root, self.root / "app.apk", None, None, "cert")
            call.assert_not_called()

    def test_code_75_stops_without_starting_next_stage(self):
        plan = {"plan_sha256": "fixture"}
        with patch.object(release.sys, "platform", "linux"), patch.object(release, "GUARD", Path(__file__)), \
                patch.object(release, "LATEST", self.root / "no-latest.json"), \
                patch.object(release, "verified_plan", return_value=plan), \
                patch.object(release, "run", return_value=subprocess.CompletedProcess([], 75)) as call, \
                contextlib.redirect_stdout(io.StringIO()):
            with self.assertRaises(ValueError):
                release.execute(self.root, self.root, "lint")
            self.assertEqual(call.call_count, 1)
            self.assertEqual(support.load(self.root / "format.json")["return_code"], 75)
            with self.assertRaises(ValueError):
                release.execute(self.root, self.root, "lint")
            self.assertEqual(call.call_count, 1)

    def test_apk_must_match_guarded_assembly_before_signature_tools_run(self):
        for stage in release.STAGES:
            guard_path = self.root / (stage + "-guard-private.json")
            support.save(guard_path, {"status": "success", "return_code": 0})
            support.save(self.root / (stage + ".json"), {
                "status": "passed", "plan_sha256": "fixture", "guard_sha256": support.digest(guard_path),
                "apk": {"sha256": "different", "bytes": 3}})
        apk = self.root / "app.apk"
        apk.write_bytes(b"apk")
        with patch.object(release, "verified_plan", return_value={"plan_sha256": "fixture"}), \
                patch.object(release, "run") as call:
            with self.assertRaisesRegex(ValueError, "guarded assembly"):
                release.certify(self.root, self.root, apk, None, None, "cert")
            call.assert_not_called()

    def test_new_crash_during_case_makes_checkpoint_sticky(self):
        pilot = phone.Phone.__new__(phone.Phone)
        pilot.directory, pilot.serial = self.root, "fixture"
        plan = {"cases": [{"id": "one", "steps": [{"op": "media"}]}]}
        manifest = {"sha256": "a", "version_code": 25, "version": "beta"}
        with patch.object(pilot, "history", return_value={phone.PACKAGE: {}, phone.BLUETOOTH: {}}), \
                patch.object(pilot, "health", side_effect=ValueError("New process exit")), \
                patch.object(pilot, "step") as step:
            with self.assertRaises(ValueError):
                phone.suite(pilot, plan, manifest)
            self.assertEqual(support.load(self.root / "session.json")["status"], "stopped")
            step.assert_not_called()

    def test_junit_zero_or_failed_is_not_success(self):
        folder = self.root / "core/player/build/test-results/testDebugUnitTest"
        folder.mkdir(parents=True)
        for text in ('<testsuite tests="0"/>', '<testsuite tests="3" skipped="3"/>',
                     '<testsuite tests="3" failures="1"/>'):
            (folder / "TEST-example.xml").write_text(text)
            with self.assertRaises(ValueError):
                release.test_counts(self.root)
        (folder / "TEST-example.xml").write_text('<testsuite tests="3" failures="0" errors="0" skipped="1"/>')
        self.assertEqual(release.test_counts(self.root)["tests"], 3)


if __name__ == "__main__":
    unittest.main()

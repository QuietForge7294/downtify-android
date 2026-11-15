import contextlib
import io
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import release_pipeline as release


class ReleaseGateTest(unittest.TestCase):
    def run_fixture(self, root, plan, callback, *, allow_downloads=False, until='format'):
        guard = root/'guard'
        guard.touch()
        with patch.object(release.sys, 'platform', 'linux'), patch.object(release, 'GUARD', guard), \
                patch.object(release, 'LATEST', root/'latest.json'), \
                patch.object(release, 'verified_plan', return_value=plan), \
                patch.object(release, 'run', side_effect=callback) as run, \
                contextlib.redirect_stdout(io.StringIO()):
            release.execute(root, root, until, allow_dependency_downloads=allow_downloads)
        return run

    def fresh_receipt(self, root, argv, code=0):
        proof = root/'fresh-guard.json'
        release.save(proof, dict(status='success' if code == 0 else 'failed', return_code=code, memory_peak=1234))
        release.save(root/'latest.json', dict(receipt=str(proof)))
        return subprocess.CompletedProcess(argv, code, b'')

    def test_failed_build_retains_fresh_guard_evidence_and_report_without_retry(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            plan = dict(plan_sha256='fixture', label='failed', source={})
            release.save(root/'plan.json', plan)
            calls = []
            def failed(argv, *args, **kwargs):
                calls.append(argv)
                return self.fresh_receipt(root, argv, code=1)
            with self.assertRaisesRegex(ValueError, 'exit 1'):
                self.run_fixture(root, plan, failed, until='static')
            receipt = release.load(root/'format.json')
            self.assertEqual(receipt['status'], 'stopped')
            self.assertEqual(receipt['guard_evidence'], 'fresh')
            self.assertEqual(receipt['guard_sha256'], release.digest(root/'format-guard-private.json'))
            self.assertEqual(release.load(root/'format-guard-private.json')['return_code'], 1)
            self.assertEqual(len(calls), 1)
            self.assertFalse((root/'static.json').exists())
            row = release.report(root)['stages'][0]
            self.assertEqual(row['recorded_status'], 'stopped')
            self.assertEqual(row['guard_return_code'], 1)
            self.assertEqual(row['memory_peak_bytes'], 1234)
            with self.assertRaisesRegex(ValueError, 'no automatic retry'):
                self.run_fixture(root, plan, failed)
            self.assertEqual(len(calls), 1)

    def test_changed_latest_pointer_cannot_recycle_old_receipt(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            old = root/'old-guard.json'
            release.save(old, dict(status='success', return_code=0))
            release.save(root/'latest.json', dict(receipt=str(old)))
            def stale(argv, *args, **kwargs):
                release.save(root/'latest.json', dict(receipt=str(old), changed='unrelated'))
                return subprocess.CompletedProcess(argv, 0, b'')
            with self.assertRaisesRegex(ValueError, 'No fresh guard receipt'):
                self.run_fixture(root, dict(plan_sha256='fixture'), stale)
            self.assertEqual(release.load(root/'format.json')['guard_evidence'], 'stale')
            self.assertFalse((root/'format-guard-private.json').exists())

    def test_failed_guard_without_fresh_evidence_keeps_original_exit_code(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            def refused(argv, *args, **kwargs):
                return subprocess.CompletedProcess(argv, 75, b'')
            with self.assertRaisesRegex(ValueError, 'exit 75'):
                self.run_fixture(root, dict(plan_sha256='fixture'), refused)
            receipt = release.load(root/'format.json')
            self.assertEqual(receipt['return_code'], 75)
            self.assertEqual(receipt['guard_evidence'], 'missing')

    def test_historical_plan_is_offline_and_cannot_authorize_downloads(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            plan = dict(plan_sha256='fixture')
            commands = []
            def succeeded(argv, *args, **kwargs):
                commands.append(argv)
                return self.fresh_receipt(root, argv)
            self.run_fixture(root, plan, succeeded)
            self.assertIn('--offline', commands[0])
            with self.assertRaisesRegex(ValueError, 'policy differs'):
                self.run_fixture(root, plan, succeeded, allow_downloads=True)
            self.assertEqual(len(commands), 1)

    def test_network_mode_requires_matching_plan_and_changes_only_offline_argument(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            plan = dict(plan_sha256='fixture', allow_dependency_downloads=True)
            commands = []
            def succeeded(argv, *args, **kwargs):
                commands.append(argv)
                return self.fresh_receipt(root, argv)
            with self.assertRaisesRegex(ValueError, 'policy differs'):
                self.run_fixture(root, plan, succeeded)
            self.run_fixture(root, plan, succeeded, allow_downloads=True)
            self.assertEqual(commands, [[str(root/'guard'), '--wrapper', str(root/'gradlew'),
                                         '--', 'spotlessCheck', '--console=plain']])

    def test_plan_pins_network_permission_and_legacy_plan_verifies_offline(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            with patch.object(release, 'source', return_value={'tree_sha256': 'source', 'commit': 'fixture'}):
                offline = release.make_plan(root, 'fixture')
                online = release.make_plan(root, 'fixture', True)
                self.assertFalse(offline['allow_dependency_downloads'])
                self.assertTrue(online['allow_dependency_downloads'])
                self.assertNotEqual(offline['plan_sha256'], online['plan_sha256'])
                legacy = {key: value for key, value in offline.items()
                          if key not in {'allow_dependency_downloads', 'plan_sha256'}}
                legacy['plan_sha256'] = release.identity(legacy)
                release.save(root/'plan.json', legacy)
                self.assertFalse(release.verified_plan(root, root).get('allow_dependency_downloads', False))

    def test_sdk35_and_sdk37_preserve_the_same_certificate_identity(self):
        digest = 'ab' * 32
        for prefix in ('Signer #1', 'V2 Signer:', 'V3 Signer #1:'):
            output = f'{prefix} certificate SHA-256 digest: {digest.upper()}\n'
            self.assertEqual(release.signer_certificates(output), [digest])

    def test_multiple_signers_cannot_be_accepted_as_one(self):
        first, second = 'ab' * 32, 'cd' * 32
        output = (f'V2 Signer #1: certificate SHA-256 digest: {first}\n'
                  f'V2 Signer #2: certificate SHA-256 digest: {second}\n')
        self.assertEqual(release.signer_certificates(output), [first, second])
        self.assertNotEqual(release.signer_certificates(output), [first])

    def test_unrecognised_or_short_certificate_output_fails_closed(self):
        self.assertEqual(release.signer_certificates('Signer #1 certificate SHA-256 digest: abc\n'), [])
        self.assertEqual(release.signer_certificates('Unknown Signer: certificate SHA-256 digest: '+ 'ab'*32), [])

    def test_app_results_cannot_be_replaced_by_passing_player_results(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for module, failure in (('core/player', 0), ('app', 1)):
                folder = root/module/'build/test-results/testDebugUnitTest'
                folder.mkdir(parents=True)
                (folder/'TEST-fixture.xml').write_text(f'<testsuite tests="4" failures="{failure}"/>')
            self.assertEqual(release.test_counts(root)['tests'], 4)
            with self.assertRaises(ValueError):
                release.test_counts(root, 'app')

    def test_failed_app_suite_stops_before_any_apk_command(self):
        self.assertIn('app-tests', release.STAGES)
        self.assertLess(list(release.STAGES).index('app-tests'), list(release.STAGES).index('apk'))
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            guard, latest = root/'guard', root/'latest.json'
            guard.touch()
            plan = {'plan_sha256': 'fixture'}
            for stage in release.STAGES:
                if stage == 'app-tests':
                    break
                release.save(root/(stage+'.json'), dict(status='passed', plan_sha256='fixture'))
            folder = root/'app/build/test-results/testDebugUnitTest'
            folder.mkdir(parents=True)
            (folder/'TEST-fixture.xml').write_text('<testsuite tests="2" failures="1"/>')

            def fake_run(argv, *args, **kwargs):
                proof = root/'guard-receipt.json'
                proof.write_text(json.dumps(dict(status='success', return_code=0)))
                latest.write_text(json.dumps(dict(receipt=str(proof))))
                return subprocess.CompletedProcess(argv, 0, b'')

            with patch.object(release.sys, 'platform', 'linux'), patch.object(release, 'GUARD', guard), \
                    patch.object(release, 'LATEST', latest), patch.object(release, 'verified_plan', return_value=plan), \
                    patch.object(release, 'configure_sdk', return_value='fixture-sdk'), \
                    patch.object(release, 'run', side_effect=fake_run) as run:
                with self.assertRaises(ValueError):
                    release.execute(root, root, 'apk')
                self.assertEqual(run.call_count, 1)
                self.assertIn(':app:testDebugUnitTest', run.call_args.args[0])
                self.assertFalse((root/'apk.json').exists())


if __name__ == '__main__':
    unittest.main()

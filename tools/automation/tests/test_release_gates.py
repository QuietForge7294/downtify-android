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

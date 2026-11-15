import contextlib
import io
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import release_pipeline as release


class BetaVersionTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix='downtify-beta-version-', dir=Path(__file__).resolve().parents[4])
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.version = {'code': 28, 'name': '0.4.0-beta19'}
        self.source = {'commit': 'fixture', 'tree_sha256': 'source'}

    def test_version_pair_validates_boundaries_and_rejects_arbitrary_arguments(self):
        self.assertIsNone(release.validate_beta_version())
        for code in (1, 28, 2_100_000_000):
            self.assertEqual(release.validate_beta_version(code, '0.4.0-beta19+test_1'),
                             {'code': code, 'name': '0.4.0-beta19+test_1'})
        for code, name in ((28, None), (None, 'beta'), (0, 'beta'), (-1, 'beta'),
                           (2_100_000_001, 'beta'), (True, 'beta'), (28.0, 'beta'), ('28', 'beta'),
                           (28, ''), (28, 'a'*81), (28, 'béta'), (28, 'beta 19'),
                           (28, 'beta\n'), (28, '-Xmx8g'), (28, 'beta;-Dfoo=bar')):
            with self.subTest(code=code, name=name), self.assertRaises(ValueError):
                release.validate_beta_version(code, name)

    def test_cli_invalid_or_half_pair_stops_before_output_or_guard(self):
        for flags in (['--beta-version-code', '28'], ['--beta-version-name', 'beta19'],
                      ['--beta-version-code', '0', '--beta-version-name', 'beta19'],
                      ['--beta-version-code', '28', '--beta-version-name', 'beta 19']):
            with self.subTest(flags=flags), patch.object(sys, 'argv', ['release', 'plan', '--out', 'unused', *flags]), \
                    patch.object(release, 'private_directory') as directory, patch.object(release, 'run') as run, \
                    contextlib.redirect_stderr(io.StringIO()):
                with self.assertRaises(SystemExit) as stopped:
                    release.main()
                self.assertEqual(stopped.exception.code, 2)
                directory.assert_not_called()
                run.assert_not_called()

    def test_plan_hash_pins_version_and_legacy_plan_does_not_invent_one(self):
        with patch.object(release, 'source', return_value=self.source):
            original = release.make_plan(self.root, 'fixture')
            first = release.make_plan(self.root, 'fixture', beta_version_code=28, beta_version_name='0.4.0-beta19')
            second = release.make_plan(self.root, 'fixture', beta_version_code=29, beta_version_name='0.4.0-beta20')
            self.assertIsNone(original['pinned_beta_version'])
            self.assertEqual(first['pinned_beta_version'], self.version)
            self.assertEqual(len({item['plan_sha256'] for item in (original, first, second)}), 3)
            legacy = {key: value for key, value in original.items()
                      if key not in {'allow_dependency_downloads', 'pinned_beta_version', 'plan_sha256'}}
            legacy['plan_sha256'] = release.identity(legacy)
            release.save(self.root/'plan.json', legacy)
            verified = release.verified_plan(self.root, self.root)
            self.assertIsNone(release.plan_beta_version(verified))
            self.assertNotIn('pinned_beta_version', verified)

    def test_plan_rejects_invalid_typed_version_even_with_matching_hash(self):
        with patch.object(release, 'source', return_value=self.source):
            for version in ({'code': True, 'name': 'beta'}, {'code': 28},
                            {'code': 28, 'name': 'beta', 'extra': 'arbitrary'}):
                with self.subTest(version=version):
                    plan = release.make_plan(self.root, 'fixture')
                    plan['pinned_beta_version'] = version
                    plan['plan_sha256'] = release.identity({key: value for key, value in plan.items() if key != 'plan_sha256'})
                    release.save(self.root/'plan.json', plan)
                    with self.assertRaises(ValueError):
                        release.verified_plan(self.root, self.root)

    def test_run_version_mismatch_stops_before_guard_even_when_stage_was_passed(self):
        guard = self.root/'guard'
        guard.touch()
        release.save(self.root/'format.json', {'status': 'passed', 'plan_sha256': 'fixture'})
        cases = [(self.version, None, None), (self.version, 29, '0.4.0-beta19'),
                 (self.version, 28, '0.4.0-beta20'), (None, 28, '0.4.0-beta19')]
        for pinned, code, name in cases:
            plan = {'plan_sha256': 'fixture', 'pinned_beta_version': pinned}
            with self.subTest(pinned=pinned, code=code, name=name), \
                    patch.object(release.sys, 'platform', 'linux'), patch.object(release, 'GUARD', guard), \
                    patch.object(release, 'verified_plan', return_value=plan), patch.object(release, 'run') as run:
                with self.assertRaisesRegex(ValueError, 'Beta version differs'):
                    release.execute(self.root, self.root, 'format', beta_version_code=code, beta_version_name=name)
                run.assert_not_called()

    def test_guard_argv_adds_only_the_two_pinned_beta_properties(self):
        for online in (False, True):
            root = self.root/str(online)
            root.mkdir()
            guard = root/'guard'
            guard.touch()
            latest = root/'latest.json'
            plan = {'plan_sha256': 'fixture', 'allow_dependency_downloads': online,
                    'pinned_beta_version': self.version}
            def run(argv, *args, **kwargs):
                proof = root/'proof.json'
                release.save(proof, {'status': 'success', 'return_code': 0})
                release.save(latest, {'receipt': str(proof)})
                return subprocess.CompletedProcess(argv, 0, b'')
            with patch.object(release.sys, 'platform', 'linux'), patch.object(release, 'GUARD', guard), \
                    patch.object(release, 'LATEST', latest), patch.object(release, 'verified_plan', return_value=plan), \
                    patch.object(release, 'run', side_effect=run) as call, contextlib.redirect_stdout(io.StringIO()):
                release.execute(root, root, 'format', allow_dependency_downloads=online,
                                beta_version_code=28, beta_version_name='0.4.0-beta19')
            expected = [str(guard), '--wrapper', str(root/'gradlew'), '--']
            if not online:
                expected.append('--offline')
            expected += ['-PmusicAiBetaVersionCode=28', '-PmusicAiBetaVersionName=0.4.0-beta19',
                         'spotlessCheck', '--console=plain']
            self.assertEqual(call.call_count, 1)
            self.assertEqual(call.call_args.args[0], expected)

    def certification_fixture(self, version):
        plan = {'plan_sha256': 'fixture', 'source': self.source}
        if version is not None:
            plan['pinned_beta_version'] = version
        apk = self.root/'app.apk'
        apk.write_bytes(b'fixture-apk')
        for stage in release.STAGES:
            guard = self.root/(stage+'-guard-private.json')
            release.save(guard, {'status': 'success', 'return_code': 0})
            release.save(self.root/(stage+'.json'), {
                'status': 'passed', 'plan_sha256': 'fixture', 'guard_sha256': release.digest(guard),
                'apk': {'sha256': release.digest(apk), 'bytes': apk.stat().st_size},
            })
        return plan, apk

    def certify_fixture(self, plan, apk, code, name):
        metadata = f"package: name='com.henriquesebastiao.downtify.debug' versionCode='{code}' versionName='{name}'\n"
        certificate = 'ab'*32
        signature = 'V2 Signer: certificate SHA-256 digest: '+certificate+'\n'
        with patch.object(release, 'verified_plan', return_value=plan), \
                patch.object(release, 'source', return_value=self.source), \
                patch.object(release, 'run', side_effect=[subprocess.CompletedProcess([], 0, metadata.encode()),
                                                        subprocess.CompletedProcess([], 0, signature.encode())]), \
                contextlib.redirect_stdout(io.StringIO()):
            release.certify(self.root, self.root, apk, Path('aapt'), Path('apksigner'), certificate)

    def test_certification_rejects_ignored_or_partially_applied_version_properties(self):
        plan, apk = self.certification_fixture(self.version)
        for code, name in ((8, '0.4.0'), (8, '0.4.0-beta19'), (28, '0.4.0')):
            with self.subTest(code=code, name=name), self.assertRaisesRegex(ValueError, 'pinned plan'):
                self.certify_fixture(plan, apk, code, name)
            self.assertFalse((self.root/'artifact.json').exists())

    def test_certification_accepts_exact_pinned_observed_version(self):
        plan, apk = self.certification_fixture(self.version)
        self.certify_fixture(plan, apk, 28, '0.4.0-beta19')
        artifact = release.load(self.root/'artifact.json')
        self.assertEqual((artifact['version_code'], artifact['version']), (28, '0.4.0-beta19'))
        self.assertEqual(artifact['sha256'], release.digest(apk))

    def test_legacy_certification_keeps_observed_version_without_an_override(self):
        plan, apk = self.certification_fixture(None)
        self.certify_fixture(plan, apk, 8, '0.4.0')
        artifact = release.load(self.root/'artifact.json')
        self.assertEqual((artifact['version_code'], artifact['version']), (8, '0.4.0'))


if __name__ == '__main__':
    unittest.main()

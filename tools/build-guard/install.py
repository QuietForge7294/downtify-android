#!/usr/bin/python3
"""Reversible host configuration only; never changes application data or signatures."""
import hashlib
import json
import os
import shutil
import subprocess
from pathlib import Path

os.umask(0o077)
source = Path(__file__).resolve().parent
state = Path('/var/lib/downtify-build-guard')
state.mkdir(mode=0o700, exist_ok=True)
manifest = state / 'installation.json'
previous = json.loads(manifest.read_text()) if manifest.exists() else None
assert previous is None or previous['status'] == 'installing', 'Already installed: preserve original preimages'
roots = [
    Path('/root/.codex/worktrees/downtify-dj-controls/android'),
    Path('/root/opencode/Downtify/android'),
    *[Path('/root/opencode/Infra/projects/downtify') / name for name in
      ('android', 'android-baseline', 'android-prefetch', 'android-rendering')],
    *[Path('/root/opencode/Infra/projects/downtify/features-20261023') / name for name in
      ('android-combined', 'android-favorites', 'android-normalization')],
]
assert all((root / 'gradlew').is_file() and (root / 'gradle.properties').is_file() for root in roots)
assert not any(b'GradleWrapperMain' in p.read_bytes() for p in Path('/proc').glob('[0-9]*/cmdline')
               if p.exists()), 'An Android build is active; coordinate before installing'
managed = {
    'org.gradle.jvmargs': '-Xmx2g -Xms128m -XX:MaxMetaspaceSize=512m -XX:ActiveProcessorCount=2 -Dfile.encoding=UTF-8',
    'org.gradle.parallel': 'false', 'org.gradle.workers.max': '2', 'org.gradle.daemon': 'false',
    'kotlin.daemon.jvmargs': '-Xmx1g -XX:MaxMetaspaceSize=384m -XX:ActiveProcessorCount=2',
    'kotlin.compiler.execution.strategy': 'daemon',
}
guard = b'''# Downtify host resource guard: keep before any Java process is started.
if [ -x /usr/local/bin/downtify-android-build ] && [ "$(hostname)" = opencode ]; then
    case "$(cat /proc/self/cgroup)" in
        */downtify-android-build.service) ;;
        *) exec /usr/local/bin/downtify-android-build --wrapper "$0" -- "$@" ;;
    esac
fi

'''
records = previous['records'] if previous else []
registered = {row['path']: row for row in records}
for row in records:
    assert hashlib.sha256(Path(row['path']).read_bytes()).hexdigest() == row['after_sha256'], 'Later edit preserved'
def digest(data):
    return hashlib.sha256(data).hexdigest()

def write(path, data, mode=None):
    if str(path) in registered:
        assert registered[str(path)]['after_sha256'] == digest(data), 'Installed data changed; inspect before resuming'
        return
    existed = path.exists()
    original = path.read_bytes() if existed else None
    before_mode = path.stat().st_mode & 0o777 if existed else None
    index = len(records)
    if existed:
        backup = state / ('preimage-' + str(index))
        backup.write_bytes(original)
        backup.chmod(0o600)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(data)
    path.chmod(mode if mode is not None else (before_mode or 0o600))
    records.append({'path': str(path), 'existed': existed, 'before_sha256': digest(original) if existed else None,
                    'after_sha256': digest(data), 'before_mode': before_mode, 'preimage': index if existed else None})
    manifest.write_text(json.dumps({'records': records, 'status': 'installing'}, indent=2))
    manifest.chmod(0o600)

def configure(path):
    lines = path.read_text().splitlines() if path.exists() else []
    retained = [line for line in lines if line.partition('=')[0].strip() not in managed
                and not line.startswith('# Downtify build resource budget on opencode;')]
    retained += ['# Downtify build resource budget on opencode; launcher enforces CLI limits.']
    retained += [key + '=' + value for key, value in managed.items()]
    write(path, ('\n'.join(retained) + '\n').encode())

for root in roots:
    wrapper = root / 'gradlew'
    original = wrapper.read_bytes()
    if str(wrapper) not in registered:
        lines = original.splitlines(keepends=True)
        assert lines[0].rstrip() in (b'#!/bin/sh', b'#!/usr/bin/env sh')
        assert b'Downtify host resource guard' not in original
        write(wrapper, b'#!/bin/sh\n\n' + guard + b''.join(lines[1:]).replace(b'\r\n', b'\n'), 0o755)
    configure(root / 'gradle.properties')
configure(Path('/root/.gradle/gradle.properties'))
targets = {
    Path('/usr/local/bin/downtify-android-build'): source / 'downtify-android-build.py',
    Path('/root/.gradle/init.d/downtify-resource-budget.gradle'): source / 'downtify-resource-budget.gradle',
    Path('/etc/systemd/system/downtify-android-build.slice'): source / 'downtify-android-build.slice',
}
for target, origin in targets.items():
    assert not target.exists() or str(target) in registered, 'Existing managed destination: stop and reconcile'
    write(target, origin.read_bytes(), 0o755 if target.name == 'downtify-android-build' else 0o644)
subprocess.run(['systemd-analyze', 'verify', '/etc/systemd/system/downtify-android-build.slice'], check=True)
subprocess.run(['systemctl', 'daemon-reload'], check=True)
subprocess.run(['systemctl', 'start', 'downtify-android-build.slice'], check=True)
manifest.write_text(json.dumps({'records': records, 'status': 'installed', 'projects': len(roots)}, indent=2))
manifest.chmod(0o600)
print(json.dumps({'installed_projects': len(roots), 'managed_files': len(records), 'manifest': str(manifest)}))

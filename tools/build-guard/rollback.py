#!/usr/bin/python3
"""Restore only exact files installed by install.py; preserve later edits."""
import hashlib
import json
import subprocess
from pathlib import Path

state = Path('/var/lib/downtify-build-guard')
manifest = json.loads((state / 'installation.json').read_text())
status = subprocess.run(['systemctl', 'show', 'downtify-android-build.service', '-p', 'ActiveState', '--value'],
                        capture_output=True, text=True).stdout.strip()
assert status not in ('active', 'activating', 'deactivating'), 'A guarded build is still active'
records = manifest['records']
for row in records:
    path = Path(row['path'])
    if row.get('ephemeral') and not path.exists():
        continue
    assert path.is_file() and hashlib.sha256(path.read_bytes()).hexdigest() == row['after_sha256'], \
        'Later edit preserved; reconcile manually: ' + str(path)
subprocess.run(['systemctl', 'stop', 'downtify-android-build.slice'], check=True)
for row in reversed(records):
    path = Path(row['path'])
    if row.get('ephemeral') and not path.exists():
        continue
    if row['existed']:
        path.write_bytes((state / ('preimage-' + str(row['preimage']))).read_bytes())
        path.chmod(row['before_mode'])
    else:
        path.unlink()
subprocess.run(['systemctl', 'daemon-reload'], check=True)
print('Host build configuration restored; application files, APKs and caches untouched.')

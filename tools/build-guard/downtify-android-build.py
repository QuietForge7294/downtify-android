#!/usr/bin/python3
"""Host-wide single-flight Android builds. Receipts never contain arbitrary arguments/env."""
import fcntl
import json
import os
import re
import select
import signal
import subprocess
import sys
import time
import uuid
from datetime import datetime, timezone
from pathlib import Path

UNIT = 'downtify-android-build.service'
SLICE = 'downtify-android-build.slice'
STATE = Path('/var/lib/downtify-build-guard')
LOCK = Path('/run/lock/downtify-android-build.lock')
JDK = '/opt/jdk/jdk-17.0.21+9'
GRADLE_HEAP = '-Xmx2g -Xms128m -XX:MaxMetaspaceSize=512m -XX:ActiveProcessorCount=2 -Dfile.encoding=UTF-8'
KOTLIN_HEAP = '-Xmx1g -XX:MaxMetaspaceSize=384m -XX:ActiveProcessorCount=2'
LIMIT = 4 * 1024**3


def save(path, value):
    temporary = path.with_suffix('.tmp')
    temporary.write_text(json.dumps(value, indent=2))
    temporary.chmod(0o600)
    temporary.replace(path)


def read(path, default=''):
    try:
        return Path(path).read_text().strip()
    except OSError:
        return default


def pressure(path):
    result = {}
    for line in read(path).splitlines():
        kind, *fields = line.split()
        result[kind] = {k: float(v) for k, v in (field.split('=') for field in fields)}
    return result


def counters(path):
    return {line.split()[0]: int(line.split()[1]) for line in read(path).splitlines()}


def unit_properties():
    result = subprocess.run(
        ['systemctl', 'show', UNIT, '-p', 'ActiveState', '-p', 'Result', '-p', 'ExecMainStatus', '-p', 'MemoryPeak'],
        text=True, capture_output=True,
    )
    return dict(line.split('=', 1) for line in result.stdout.splitlines() if '=' in line)


def lock_file():
    descriptor = os.open(LOCK, os.O_RDWR | os.O_CREAT | os.O_NOFOLLOW, 0o600)
    os.set_inheritable(descriptor, True)
    return os.fdopen(descriptor, 'r+')


def busy():
    with lock_file() as stream:
        try:
            fcntl.flock(stream, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            return True
    return unit_properties().get('ActiveState') in {'active', 'activating', 'deactivating', 'failed'}


def process_sample(group):
    result = []
    for value in read(group / 'cgroup.procs').split():
        root = Path('/proc') / value
        try:
            args = (root / 'cmdline').read_bytes().decode(errors='replace').split('\0')
            status = dict(line.split(':', 1) for line in (root / 'status').read_text().splitlines() if ':' in line)
            name = os.path.basename(os.readlink(root / 'exe'))
            role = next((kind for kind in ('GradleDaemon', 'KotlinCompileDaemon', 'GradleWorkerMain', 'GradleWrapperMain')
                         if any(kind in arg for arg in args)), name)
            result.append({
                'pid': int(value), 'uid': int(status['Uid'].split()[0]), 'role': role,
                'java': name == 'java', 'rss': int(status.get('VmRSS', '0 kB').split()[0]) * 1024,
                'heap': next((arg for arg in args if re.fullmatch(r'-Xmx[0-9]+[kKmMgG]', arg)), None),
            })
        except (OSError, KeyError, ValueError):
            pass
    return result


def cleanup_group(group):
    """Only processes in THIS service cgroup, never global Java matching."""
    targets = {int(value) for value in read(group / 'cgroup.procs').split()} - {os.getpid()}
    descriptors = []
    for pid in targets:
        try:
            descriptors.append(os.pidfd_open(pid))
            os.kill(pid, signal.SIGTERM)
        except ProcessLookupError:
            pass
    deadline = time.monotonic() + 5
    pending = list(descriptors)
    while pending and time.monotonic() < deadline:
        finished, _, _ = select.select(pending, [], [], max(0, deadline - time.monotonic()))
        pending = [fd for fd in pending if fd not in finished]
    for value in read(group / 'cgroup.procs').split():
        pid = int(value)
        if pid != os.getpid():
            try:
                os.kill(pid, signal.SIGKILL)
            except ProcessLookupError:
                pass
    for descriptor in descriptors:
        os.close(descriptor)
    return sorted(targets)


def inner(run_id, wrapper, arguments):
    location = read('/proc/self/cgroup').split('::', 1)[1]
    if not location.endswith('/' + UNIT):
        raise RuntimeError('The guarded runner must be started by its systemd service')
    group = Path('/sys/fs/cgroup') / location.lstrip('/')
    if int(read(group / 'memory.max', '0')) != LIMIT:
        raise RuntimeError('Memory limit is not active; build refused')
    receipt_path = STATE / 'runs' / run_id / 'receipt.json'
    with lock_file() as stream:
        try:
            fcntl.flock(stream, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            print('Build lock held; no JVM started.', flush=True)
            return 75
        stream.seek(0)
        stream.truncate()
        json.dump({'pid': os.getpid(), 'project': str(wrapper.parent), 'run': run_id, 'unit': UNIT}, stream)
        stream.flush()
        os.environ['DOWNTIFY_BUILD_GUARDED'] = 'v1'
        os.environ['JAVA_HOME'] = JDK
        os.environ['GRADLE_USER_HOME'] = '/root/.gradle'
        command = [str(wrapper), '--no-daemon', '--no-parallel', '--max-workers=2', '--no-watch-fs',
                   '-Dorg.gradle.jvmargs=' + GRADLE_HEAP, '-Dorg.gradle.java.home=' + JDK,
                   '-Pkotlin.daemon.jvmargs=' + KOTLIN_HEAP, '-Pkotlin.compiler.execution.strategy=daemon'] + arguments
        if arguments == ['--guard-probe']:
            command = ['/usr/bin/true']
        start = time.monotonic()
        receipt = {'status': 'running', 'utc': datetime.now(timezone.utc).isoformat(), 'host': 'opencode',
                   'project': str(wrapper.parent), 'unit': UNIT, 'cgroup': location,
                   'budget_bytes': LIMIT, 'gradle_heap': '2GiB', 'kotlin_heap': '1GiB', 'max_workers': 2,
                   'parallel': False, 'jdk': JDK, 'peak_jvms': 0, 'peak_test_worker_jvms': 0,
                   'peak_rss_sum': 0, 'processes_observed': {}, 'peak_memory_current': 0,
                   'memory_events_before': counters(group / 'memory.events'),
                   'lxc_memory_pressure_before': pressure('/proc/pressure/memory'),
                   'lxc_io_pressure_before': pressure('/proc/pressure/io')}
        save(receipt_path, receipt)
        child = subprocess.Popen(command, cwd=wrapper.parent, pass_fds=(stream.fileno(),))
        def terminate(signum, frame):
            child.terminate()
        signal.signal(signal.SIGTERM, terminate)
        signal.signal(signal.SIGINT, terminate)
        while True:
            sample = process_sample(group)
            receipt['peak_jvms'] = max(receipt['peak_jvms'], sum(row['java'] for row in sample))
            receipt['peak_test_worker_jvms'] = max(receipt['peak_test_worker_jvms'], sum(row['role'] == 'GradleWorkerMain' for row in sample))
            receipt['peak_rss_sum'] = max(receipt['peak_rss_sum'], sum(row['rss'] for row in sample))
            receipt['peak_memory_current'] = max(receipt['peak_memory_current'], int(read(group / 'memory.current', '0')))
            receipt['memory_peak'] = int(read(group / 'memory.peak', '0'))
            receipt['memory_high'] = int(read(group / 'memory.high', '0'))
            receipt['memory_pressure'] = pressure(group / 'memory.pressure')
            receipt['io_pressure'] = pressure(group / 'io.pressure')
            receipt['elapsed_seconds'] = round(time.monotonic() - start, 3)
            for row in sample:
                receipt['processes_observed'][str(row['pid'])] = {k: v for k, v in row.items() if k != 'pid'}
            save(receipt_path, receipt)
            if child.poll() is not None:
                break
            # Resource sampling requested by the owner, not a retry/wait-for-success loop.
            try:
                child.wait(timeout=0.5)
            except subprocess.TimeoutExpired:
                pass
        receipt['cleanup_pids'] = cleanup_group(group)
        receipt['memory_events_after'] = counters(group / 'memory.events')
        receipt['lxc_memory_pressure_after'] = pressure('/proc/pressure/memory')
        receipt['lxc_io_pressure_after'] = pressure('/proc/pressure/io')
        receipt['cpu'] = counters(group / 'cpu.stat')
        receipt['return_code'] = child.returncode
        receipt['status'] = 'success' if child.returncode == 0 else 'failed'
        save(receipt_path, receipt)
        print(json.dumps({'build_receipt': str(receipt_path), 'status': receipt['status'],
                          'memory_peak': receipt['memory_peak'], 'elapsed_seconds': receipt['elapsed_seconds']}), flush=True)
        return child.returncode if child.returncode >= 0 else 128 - child.returncode


def outer(wrapper, arguments):
    if os.geteuid() != 0:
        print('This opencode build runner is installed for the existing root build account.', file=sys.stderr)
        return 77
    if busy():
        print('Downtify build lock/unit busy (or previous failure needs review); no JVM started.', file=sys.stderr)
        return 75
    if arguments == ['--lock-status']:
        print('Downtify build lock free.')
        return 0
    forbidden = ('--parallel', '--daemon', '--stop', '--gradle-user-home', '-g',
                 '-Dorg.gradle.jvmargs', '-Dorg.gradle.java.home', '-Pkotlin.daemon.jvmargs',
                 '-Pkotlin.compiler.execution.strategy', '-Dkotlin.daemon.jvm.options')
    if any(arg == key or arg.startswith(key + '=') for arg in arguments for key in forbidden):
        print('Build resource overrides are managed by the host guard; request refused.', file=sys.stderr)
        return 64
    if any(arg.startswith('--max-workers') and arg not in ('--max-workers=1', '--max-workers=2') for arg in arguments):
        print('At most two Gradle workers are allowed.', file=sys.stderr)
        return 64
    # JVM option variables can override even an explicit -Xmx; fail closed, never print values.
    for name in ('JAVA_OPTS', 'GRADLE_OPTS', 'JAVA_TOOL_OPTIONS', '_JAVA_OPTIONS', 'JDK_JAVA_OPTIONS'):
        value = os.environ.get(name, '')
        if value and name in ('JAVA_TOOL_OPTIONS', '_JAVA_OPTIONS', 'JDK_JAVA_OPTIONS'):
            print(name + ' is nonempty and Java may echo its value; review it before building.', file=sys.stderr)
            return 64
        if re.search(r'-Xm[xs]|MaxRAM|MaxHeapSize|org\.gradle\.(jvmargs|parallel|workers)|kotlin\.daemon', value):
            print('Conflicting resource override in ' + name + '; no JVM started.', file=sys.stderr)
            return 64
    available = next(int(line.split()[1]) * 1024 for line in read('/proc/meminfo').splitlines() if line.startswith('MemAvailable:'))
    if (available < 5 * 1024**3 or pressure('/proc/pressure/memory').get('full', {}).get('avg10', 0) > 5
            or pressure('/proc/pressure/io').get('full', {}).get('avg10', 0) > 15):
        print('Insufficient LXC memory/I/O headroom; build refused without retry.', file=sys.stderr)
        return 75
    run_id = datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ-') + uuid.uuid4().hex[:8]
    directory = STATE / 'runs' / run_id
    directory.mkdir(mode=0o700, parents=True)
    save(STATE / 'latest.json', {'run': run_id, 'receipt': str(directory / 'receipt.json')})
    environment = dict(os.environ)
    environment.update(JAVA_HOME=JDK, GRADLE_USER_HOME='/root/.gradle', PYTHONDONTWRITEBYTECODE='1')
    inherited = {'PATH', 'LANG', 'LC_ALL', 'JAVA_HOME', 'GRADLE_USER_HOME', 'ANDROID_HOME', 'ANDROID_SDK_ROOT',
                 'JAVA_OPTS', 'GRADLE_OPTS', 'JAVA_TOOL_OPTIONS', '_JAVA_OPTIONS', 'JDK_JAVA_OPTIONS',
                 'DOWNTIFY_KEYSTORE_FILE', 'DOWNTIFY_KEYSTORE_PASSWORD', 'DOWNTIFY_KEY_ALIAS', 'DOWNTIFY_KEY_PASSWORD',
                 'HTTP_PROXY', 'HTTPS_PROXY', 'ALL_PROXY', 'NO_PROXY', 'http_proxy', 'https_proxy', 'all_proxy', 'no_proxy'}
    command = ['systemd-run', '--no-ask-password', '--quiet', '--wait', '--pipe', '--service-type=exec',
               '--unit=' + UNIT, '--slice=' + SLICE, '--working-directory=' + str(wrapper.parent),
               '-p', 'MemoryAccounting=yes', '-p', 'MemoryHigh=3584M', '-p', 'MemoryMax=4G',
               '-p', 'MemorySwapMax=0', '-p', 'CPUQuota=200%', '-p', 'TasksMax=256',
               '-p', 'OOMPolicy=stop', '-p', 'KillMode=control-group', '-p', 'TimeoutStopSec=20s',
               '-p', 'RuntimeMaxSec=20min']
    for name in sorted(inherited.intersection(environment)):
        command += ['-E', name]  # Pass by NAME only, never a credential in argv/logs.
    command += [str(Path(__file__).resolve()), '--internal-run', run_id, str(wrapper), '--'] + arguments
    result = subprocess.run(command, env=environment)
    properties = unit_properties()
    save(directory / 'systemd-result.json', {'return_code': result.returncode, 'properties': properties})
    path = directory / 'receipt.json'
    if path.exists():
        receipt = json.loads(path.read_text())
        if receipt.get('status') == 'running':
            receipt.update(status='oom' if properties.get('Result') == 'oom-kill' else 'interrupted',
                           systemd=properties, return_code=result.returncode)
            peak = properties.get('MemoryPeak', '')
            if peak.isdigit():
                receipt['memory_peak'] = max(receipt.get('memory_peak', 0), int(peak))
            save(path, receipt)
    if result.returncode:
        print('Build stopped: ' + properties.get('Result', 'failed') + '; no automatic retry.', file=sys.stderr)
    print('Guard receipts: ' + str(directory), flush=True)
    return result.returncode


def main():
    os.umask(0o077)
    arguments = sys.argv[1:]
    if arguments[:1] == ['--internal-run']:
        return inner(arguments[1], Path(arguments[2]).resolve(), arguments[4:])
    if arguments[:1] != ['--wrapper'] or len(arguments) < 3 or arguments[2] != '--':
        print('Usage: downtify-android-build --wrapper /absolute/project/gradlew -- TASKS', file=sys.stderr)
        return 64
    wrapper = Path(arguments[1]).resolve()
    if wrapper.name != 'gradlew' or not wrapper.is_file():
        return 64
    return outer(wrapper, arguments[3:])


if __name__ == '__main__':
    sys.exit(main())

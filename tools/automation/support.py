"""Small shared helpers. Logs contain commands and results, never raw device output."""
import contextlib
import datetime as dt
import hashlib
import json
import os
from pathlib import Path
import socket
import subprocess
import time


def digest(path):
    with Path(path).open("rb") as handle:
        return hashlib.file_digest(handle, "sha256").hexdigest()


def identity(value):
    return hashlib.sha256(json.dumps(value, sort_keys=True, separators=(",", ":")).encode()).hexdigest()


def save(path, value):
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    temp = path.with_suffix(path.suffix + ".tmp")
    temp.write_text(json.dumps(value, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    temp.replace(path)


def load(path):
    return json.loads(Path(path).read_text(encoding="utf-8-sig"))


def paris(utc):
    # EU DST boundaries, for the operational period covered by this tool (1996+).
    # No tzdata package is needed on Windows. UTC remains the authoritative timestamp.
    def last_sunday(month):
        end = dt.datetime(utc.year, month + 1, 1, tzinfo=dt.timezone.utc) - dt.timedelta(days=1)
        return end.replace(hour=1) - dt.timedelta(days=(end.weekday() + 1) % 7)
    offset = 2 if last_sunday(3) <= utc < last_sunday(10) else 1
    return utc.astimezone(dt.timezone(dt.timedelta(hours=offset))).isoformat()


def audit(path, argv, code, started, elapsed, script, cwd=None):
    row = {"utc": started.isoformat(), "europe_paris": paris(started),
           "host": socket.gethostname(), "cwd": str(cwd or os.getcwd()), "command": argv,
           "return_code": code, "duration_ms": round(elapsed * 1000),
           "script_sha256": digest(script)}
    with Path(path).open("a", encoding="utf-8") as handle:
        handle.write(json.dumps(row, ensure_ascii=False) + "\n")


def run(argv, directory, audit_path, script, display=None, log=None, timeout=60):
    """No shell; stdout is returned privately or streamed to a private log."""
    started = dt.datetime.now(dt.timezone.utc)
    before = time.monotonic()
    code = None
    try:
        with contextlib.ExitStack() as stack:
            target = stack.enter_context(Path(log).open("wb")) if log else subprocess.PIPE
            result = subprocess.run(argv, cwd=directory, stdout=target, stderr=subprocess.STDOUT,
                                    timeout=timeout, check=False)
            code = result.returncode
            return result
    finally:
        audit(audit_path, display or argv, code, started, time.monotonic() - before, script, directory)


@contextlib.contextmanager
def lock(path):
    """Advisory OS lock: a leftover file after an interruption never blocks a new process."""
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("a+b") as handle:
        handle.seek(0)
        if os.name == "nt":
            import msvcrt
            if path.stat().st_size == 0:
                handle.write(b"0")
                handle.flush()
            handle.seek(0)
            msvcrt.locking(handle.fileno(), msvcrt.LK_NBLCK, 1)
        else:
            import fcntl
            fcntl.flock(handle, fcntl.LOCK_EX | fcntl.LOCK_NB)
        try:
            yield
        finally:
            if os.name == "nt":
                handle.seek(0)
                msvcrt.locking(handle.fileno(), msvcrt.LK_UNLCK, 1)
            else:
                fcntl.flock(handle, fcntl.LOCK_UN)


def private_directory(path, repo):
    path, repo = Path(path).resolve(), Path(repo).resolve()
    if path == repo or repo in path.parents:
        raise ValueError("Run outputs must be outside the checkout (private evidence).")
    path.mkdir(parents=True, exist_ok=True)
    return path

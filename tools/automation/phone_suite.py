#!/usr/bin/env python3
"""Single-device, resumable USB scenarios. Private evidence stays outside the checkout."""
import argparse
import datetime as dt
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile
import time
import xml.etree.ElementTree as ET

from support import digest, identity, load, lock, private_directory, run, save

SCRIPT = Path(__file__).resolve()
PACKAGE = "com.henriquesebastiao.downtify.debug"
BLUETOOTH = "com.android.bluetooth"


def exits(text, package):
    if "ACTIVITY MANAGER PROCESS EXIT INFO" not in text:
        raise ValueError("Exit history unavailable; cannot establish device health.")
    if "ApplicationExitInfo #" not in text and not re.search(r"no exit info", text, re.I):
        raise ValueError("Empty/unknown exit history response.")
    result = {}
    for block in text.split("ApplicationExitInfo #")[1:]:
        stamp = re.search(r"timestamp=([^\r\n]+)", block)
        process = re.search(r"process=(\S+) reason=(\d+)", block)
        if not stamp or not process:
            raise ValueError("Unknown exit history format.")
        if process[1] == package or process[1].startswith(package + ":"):
            result[hashlib.sha256((stamp[1] + process[1]).encode()).hexdigest()] = int(process[2])
    return result


def installed(text):
    code = re.search(r"\bversionCode=(\d+)", text)
    version = re.search(r"\bversionName=([^\r\n]+)", text)
    if not code or not version:
        raise ValueError("Target app/package version is unavailable.")
    return int(code[1]), version[1].strip()


def count_queue(xml):
    values = set()
    for node in ET.fromstring(xml).iter("node"):
        for key in ("text", "content-desc"):
            match = re.fullmatch(r"\s*([\d\s\u202f\u00a0]+) éléments?\s*", node.get(key, ""))
            if match:
                values.add(int(re.sub(r"\s", "", match[1])))
    if len(values) != 1:
        raise ValueError("Queue header unavailable/ambiguous: NEEDS_VISUAL, no inferred count.")
    return values.pop()


def tap_point(xml, selector):
    if set(selector) - {"text", "description", "resource_id"} or not selector:
        raise ValueError("Use an exact text/description/resource_id selector.")
    fields = {"text": "text", "description": "content-desc", "resource_id": "resource-id"}
    matches = []
    for node in ET.fromstring(xml).iter("node"):
        if all(node.get(fields[k]) == v for k, v in selector.items()) and node.get("enabled") == "true":
            bounds = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.get("bounds", ""))
            if bounds:
                left, top, right, bottom = map(int, bounds.groups())
                if right > left and bottom > top:
                    matches.append(((left + right) // 2, (top + bottom) // 2))
    if len(matches) != 1:
        raise ValueError("Selector unavailable/ambiguous: NEEDS_VISUAL. No blind tap.")
    return matches[0]


def media_state(text):
    sessions = [s for s in text.split("package=")[1:] if s.startswith(PACKAGE)]
    if len(sessions) != 1:
        raise ValueError("Target media session missing/ambiguous.")
    session = sessions[0]
    state = re.search(r"state=PlaybackState\s*\{state=(\w+)(?:\((\d+)\))?", session)
    queue = re.search(r"(?:mQueue|queue):?\s*size=(\d+)", session, re.I)
    if not state:
        raise ValueError("PlaybackState missing; cannot treat it as paused.")
    # Android's legacy queue size is not the app's internal queue count.
    return {"state": state[1], "legacy_queue": int(queue[1]) if queue else None}


def verify_artifact(manifest, apk):
    if manifest.get("package") != PACKAGE or not isinstance(manifest.get("version_code"), int):
        raise ValueError("Unexpected package/version manifest.")
    if digest(apk) != manifest.get("sha256") or apk.stat().st_size != manifest.get("bytes"):
        raise ValueError("APK hash/length mismatch; refuse installation.")


def validate_plan(plan):
    if plan.get("schema") != 1 or not plan.get("cases"):
        raise ValueError("Expected schema 1 and nonempty cases.")
    ids = []
    for case in plan["cases"]:
        key = case["id"]
        if not re.fullmatch(r"[A-Za-z0-9._-]+", key) or key in ids:
            raise ValueError("Case IDs must be unique simple names.")
        ids.append(key)
        assertions = 0
        for step in case["steps"]:
            if step["op"] not in {"tap", "back", "wait", "capture", "queue_count", "media"}:
                raise ValueError("Unsupported operation.")
            if step["op"] == "wait" and not 0 <= step["seconds"] <= 30:
                raise ValueError("Each wait must be bounded to 0..30 seconds.")
            if step["op"] in {"queue_count", "media"}:
                if not step.get("expect"):
                    raise ValueError("Assertions require explicit expectations.")
                assertions += 1
        if not assertions:
            raise ValueError("Each automated case needs an observable assertion.")
    return plan


class Phone:
    def __init__(self, adb, directory, serial=None):
        self.adb, self.directory, self.serial = str(adb), directory, None
        self.sequence = 0
        listing = self.call("devices", "-l").decode()
        rows = re.findall(r"^(\S+)\s+(device|offline|unauthorized)\b([^\r\n]*)", listing, re.M)
        rows = [r for r in rows if serial is None or r[0] == serial]
        if len(rows) != 1 or rows[0][1] != "device" or "usb:" not in rows[0][2]:
            raise ValueError("Exactly one authorized USB device is required (no emulator/network scan).")
        self.serial = rows[0][0]

    def call(self, *args, timeout=30):
        prefix = ["-s", self.serial] if self.serial else []
        shown = ["-s", "<DEVICE>"] if self.serial else []
        result = run([self.adb, *prefix, *map(str, args)], self.directory,
                     self.directory / "commands.jsonl", SCRIPT,
                     display=["adb", *shown, *map(str, args)], timeout=timeout)
        if result.returncode:
            raise ValueError("ADB failed: exit " + str(result.returncode) + ". No retry.")
        return result.stdout

    def version(self):
        return installed(self.call("shell", "dumpsys", "package", PACKAGE).decode())

    def history(self):
        return {package: exits(self.call("shell", "dumpsys", "activity", "exit-info", package).decode(), package)
                for package in (PACKAGE, BLUETOOTH)}

    def health(self, baseline):
        current = self.history()
        for package in current:
            new = {k: v for k, v in current[package].items() if k not in baseline[package]}
            if new:
                raise ValueError("New process exit in " + package + ": " + str(sorted(new.values())))
        return current

    def foreground(self):
        trust = self.call("shell", "dumpsys", "trust").decode()
        windows = self.call("shell", "dumpsys", "window", "windows").decode()
        if not re.search(r"deviceLocked=(?:false|0)\b", trust):
            raise ValueError("Unlock state unavailable/locked. No input sent.")
        focus = re.search(r"mCurrentFocus=([^\r\n]+)", windows)
        if not focus or PACKAGE not in focus[1]:
            raise ValueError("Downtify is not focused. No input sent.")

    def capture(self, label):
        self.foreground()
        path = self.directory / (label + "-private.png")
        data = self.call("exec-out", "screencap", "-p")
        if not data.startswith(b"\x89PNG\r\n\x1a\n"):
            raise ValueError("Invalid PNG capture.")
        path.write_bytes(data)
        return {"screenshot_sha256": digest(path)}

    def xml(self):
        self.foreground()
        # A unique remote file prevents stale UI reuse. Always attempt removal.
        name = "/sdcard/downtify-suite-" + str(time.time_ns()) + ".xml"
        try:
            self.call("shell", "uiautomator", "dump", name, timeout=20)
            data = self.call("exec-out", "cat", name)
            ET.fromstring(data)
            return data
        finally:
            self.call("shell", "rm", "-f", name)

    def step(self, step, label, baseline):
        op = step["op"]
        if op == "wait":
            until = time.monotonic() + step["seconds"]
            while time.monotonic() < until:
                time.sleep(min(2, max(0, until - time.monotonic())))
                self.health(baseline)
            return {"wait_seconds": step["seconds"]}
        if op == "capture":
            return self.capture(label)
        if op == "tap":
            x, y = tap_point(self.xml(), step["selector"])
            self.foreground()
            self.call("shell", "input", "tap", x, y)
            return {"selector_matched": True}
        if op == "back":
            self.foreground()
            self.call("shell", "input", "keyevent", "4")
            return {"back": True}
        if op == "queue_count":
            value = count_queue(self.xml())
            expected = step["expect"]
            if not expected.get("min", 0) <= value <= expected.get("max", sys.maxsize):
                raise ValueError("Internal queue count outside expected range: " + str(value))
            return {"internal_queue_count": value}
        result = media_state(self.call("shell", "dumpsys", "media_session").decode())
        if any(result.get(k) != v for k, v in step["expect"].items()):
            raise ValueError("Media session assertion failed: " + json.dumps(result))
        return result


def suite(phone, plan, artifact):
    state_path = phone.directory / "session.json"
    key = identity({"plan": plan, "artifact": artifact["sha256"], "device": phone.serial})
    if state_path.exists():
        state = load(state_path)
        if state["identity"] != key or state["status"] != "ready":
            raise ValueError("Session changed or stopped; inspect evidence and prepare a new run.")
    else:
        state = {"identity": key, "baseline": phone.history(), "status": "ready", "cases": {}}
        save(state_path, state)
    try:
        for case in plan["cases"]:
            if case["id"] in state["cases"]:
                print(json.dumps({"case": case["id"], "status": "reused"}), flush=True)
                continue
            phone.health(state["baseline"])
            if phone.version() != (artifact["version_code"], artifact["version"]):
                raise ValueError("Installed candidate differs from the verified manifest.")
            state["status"] = "running"
            save(state_path, state)
            observations = []
            before = time.monotonic()
            for index, step in enumerate(case["steps"]):
                observations.append(phone.step(step, case["id"] + "-" + str(index), state["baseline"]))
                phone.health(state["baseline"])
            result = {"status": "automated_checks_passed", "observations": observations,
                      "seconds": round(time.monotonic() - before, 3),
                      "manual_checks": {k: "NOT_RUN" for k in case.get("manual_checks", [])}}
            state["cases"][case["id"]] = result
            state["status"] = "ready"
            save(state_path, state)
            print(json.dumps({"case": case["id"], **result}), flush=True)
    except BaseException as error:
        state.update(status="stopped", error_type=type(error).__name__)
        save(state_path, state)
        raise


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=["preflight", "install", "run"])
    parser.add_argument("--adb", type=Path, required=True)
    parser.add_argument("--out", type=Path, required=True)
    parser.add_argument("--serial")
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--apk", type=Path, required=True)
    parser.add_argument("--plan", type=Path)
    args = parser.parse_args()
    directory = private_directory(args.out, SCRIPT.parents[2])
    manifest = load(args.manifest)
    verify_artifact(manifest, args.apk)
    plan = validate_plan(load(args.plan)) if args.action == "run" and args.plan else None
    if args.action == "run" and plan is None:
        parser.error("run requires --plan")
    phone = Phone(args.adb.resolve(), directory, args.serial)
    cache = Path(os.environ.get("LOCALAPPDATA", tempfile.gettempdir())) / "DowntifyTests/locks"
    with lock(cache / (hashlib.sha256(phone.serial.encode()).hexdigest() + ".lock")):
        version = phone.version()
        if args.action == "preflight":
            history = phone.history()
            result = {"status": "PREFLIGHT_ONLY", "version_code": version[0], "version": version[1],
                      "exit_history_entries": {k: len(v) for k, v in history.items()},
                      "artifact_sha256": manifest["sha256"], "functional_tests": "NOT_RUN"}
            save(directory / "preflight.json", result)
            print(json.dumps(result))
        elif args.action == "install":
            marker = directory / "install.json"
            if marker.exists():
                raise ValueError("Installation already attempted. Inspect receipt; no repeated install.")
            if version[0] > manifest["version_code"]:
                raise ValueError("Newer app installed; refuse downgrade.")
            baseline = phone.history()
            save(marker, {"status": "running", "sha256": manifest["sha256"]})
            phone.call("install", "-r", str(args.apk.resolve()), timeout=180)
            if phone.version() != (manifest["version_code"], manifest["version"]):
                raise ValueError("Installed version mismatch.")
            after = phone.history()
            for package in after:
                if any(v != 16 for k, v in after[package].items() if k not in baseline[package]):
                    raise ValueError("Unexpected process exit during installation.")
            save(marker, {"status": "installed_data_preserved", "sha256": manifest["sha256"],
                          "signature": "Android enforced update compatibility; no uninstall/data clear"})
            print(json.dumps(load(marker)))
        else:
            suite(phone, plan, manifest)


if __name__ == "__main__":
    try:
        main()
    except (ValueError, OSError, subprocess.SubprocessError, ET.ParseError) as error:
        print(json.dumps({"status": "STOPPED", "reason": str(error)}), file=sys.stderr)
        sys.exit(1)

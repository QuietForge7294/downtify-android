#!/usr/bin/env python3
"""Prepare/run separate guarded Gradle stages; certify an APK only after every gate passes."""
import argparse
import datetime as dt
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

from support import digest, identity, load, lock, private_directory, run, save

SCRIPT = Path(__file__).resolve()
GUARD = Path("/usr/local/bin/downtify-android-build")
LATEST = Path("/var/lib/downtify-build-guard/latest.json")
AUDIT_PATH = None
STAGES = {
    "format": ["spotlessCheck"],
    "static": ["detekt"],
    "player-compile": [":core:player:compileDebugUnitTestKotlin"],
    "player-tests": [":core:player:testDebugUnitTest"],
    "app-compile": [":app:compileBetaKotlin"],
    "apk": [":app:assembleBeta"],
    "lint": [":app:lintBeta"],
}


def git(repo, *args):
    if AUDIT_PATH:
        result = run(["git", "-C", str(repo), *args], repo, AUDIT_PATH, SCRIPT)
        if result.returncode:
            raise ValueError("Git source inspection failed.")
        return result.stdout
    return subprocess.check_output(["git", "-C", str(repo), *args])


def source(repo):
    # Include uncommitted and untracked source, exclude ignored build outputs and caches.
    checksum = hashlib.sha256()
    for name in sorted(set(git(repo, "ls-files", "-z", "--cached", "--others", "--exclude-standard").split(b"\0"))):
        if not name:
            continue
        path = repo / os.fsdecode(name)
        checksum.update(name + b"\0")
        checksum.update(bytes.fromhex(digest(path)) if path.is_file() else b"MISSING")
    return {"commit": git(repo, "rev-parse", "HEAD").decode().strip(), "tree_sha256": checksum.hexdigest()}


def make_plan(repo, label):
    if not re.fullmatch(r"[A-Za-z0-9._-]+", label):
        raise ValueError("Use a simple release label.")
    plan = {"schema": 1, "label": label, "source": source(repo), "stages": STAGES,
            "policy": "one guarded Gradle invocation per stage; no retries; USB after final candidate"}
    return dict(plan, plan_sha256=identity(plan))


def verified_plan(directory, repo):
    plan = load(directory / "plan.json")
    unsigned = {k: v for k, v in plan.items() if k != "plan_sha256"}
    if identity(unsigned) != plan["plan_sha256"] or plan["stages"] != STAGES:
        raise ValueError("Plan changed or belongs to another tool version. Prepare a new plan.")
    if source(repo)["tree_sha256"] != plan["source"]["tree_sha256"]:
        raise ValueError("Source changed. Prepare a new plan; successful receipts cannot be reused.")
    return plan


def test_counts(repo):
    paths = list((repo / "core/player/build/test-results/testDebugUnitTest").glob("TEST-*.xml"))
    totals = {key: 0 for key in ("tests", "failures", "errors", "skipped")}
    for path in paths:
        root = ET.parse(path).getroot()
        for key in totals:
            totals[key] += int(root.get(key, 0))
    if totals["tests"] <= totals["skipped"] or totals["failures"] or totals["errors"]:
        raise ValueError("No executed tests, missing results, or failing JUnit results.")
    return totals


def configure_sdk(repo, requested=None):
    value = requested or os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    properties = repo / "local.properties"
    if not value and properties.is_file():
        for line in properties.read_text(encoding="utf-8").splitlines():
            if line.strip().startswith("sdk.dir="):
                value = line.strip().split("=", 1)[1].replace("\\:", ":").replace("\\\\", "\\")
                value = repo / value
                break
    if not value:
        raise ValueError("Android SDK missing: use --sdk PATH, ANDROID_HOME or sdk.dir. No build started.")
    path = Path(value).resolve()
    platforms = list((path / "platforms").glob("*/android.jar"))
    tools = list((path / "build-tools").glob("*/aapt2")) + list((path / "build-tools").glob("*/aapt2.exe"))
    if not platforms or not tools:
        raise ValueError("Android SDK platform/build-tools unavailable. Reuse an installed SDK; no automatic download.")
    os.environ["ANDROID_HOME"] = str(path)
    os.environ["ANDROID_SDK_ROOT"] = str(path)
    return str(path)


def execute(repo, directory, until, sdk=None):
    if sys.platform != "linux" or not GUARD.is_file():
        raise ValueError("Build execution requires the installed opencode Linux guard.")
    plan = verified_plan(directory, repo)
    sdk_path = configure_sdk(repo, sdk) if list(STAGES).index(until) >= list(STAGES).index("player-compile") else None
    with lock(directory / "pipeline.lock"):
        for stage, tasks in STAGES.items():
            receipt_path = directory / (stage + ".json")
            if receipt_path.exists():
                receipt = load(receipt_path)
                if receipt.get("status") != "passed" or receipt.get("plan_sha256") != plan["plan_sha256"]:
                    raise ValueError("Unfinished/failed stage: inspect receipts; no automatic retry: " + stage)
                print(json.dumps({"stage": stage, "status": "reused"}), flush=True)
            else:
                verified_plan(directory, repo)
                argv = [str(GUARD), "--wrapper", str(repo / "gradlew"), "--", "--offline", *tasks, "--console=plain"]
                # Prints the full future invocation before running it. SSH callers must also announce it in chat.
                print(json.dumps({"stage": stage, "command": argv}), flush=True)
                receipt = {"stage": stage, "plan_sha256": plan["plan_sha256"], "status": "running", "sdk_path": sdk_path}
                save(receipt_path, receipt)
                old_latest = digest(LATEST) if LATEST.exists() else None
                before = time.monotonic()
                try:
                    result = run(argv, repo, directory / "commands.jsonl", SCRIPT,
                                 log=directory / (stage + "-private.log"), timeout=1300)
                    receipt.update(return_code=result.returncode, seconds=round(time.monotonic() - before, 3))
                    if result.returncode != 0:
                        raise ValueError("Guard/build failed: exit " + str(result.returncode))
                    if not LATEST.exists() or digest(LATEST) == old_latest:
                        raise ValueError("No fresh guard receipt.")
                    guard = load(Path(load(LATEST)["receipt"]))
                    save(directory / (stage + "-guard-private.json"), guard)
                    if guard.get("status") != "success" or guard.get("return_code") != 0:
                        raise ValueError("Guard receipt does not prove success.")
                    receipt["guard_sha256"] = digest(directory / (stage + "-guard-private.json"))
                    if stage == "player-tests":
                        receipt["junit"] = test_counts(repo)
                    if stage == "apk":
                        outputs = list((repo / "app/build/outputs/apk/beta").glob("*.apk"))
                        if len(outputs) != 1:
                            raise ValueError("Expected exactly one assembled beta APK.")
                        receipt["apk"] = {"sha256": digest(outputs[0]), "bytes": outputs[0].stat().st_size}
                    verified_plan(directory, repo)
                    receipt["status"] = "passed"
                except BaseException as error:
                    receipt.update(status="stopped", error_type=type(error).__name__)
                    raise
                finally:
                    save(receipt_path, receipt)
                print(json.dumps({"stage": stage, "status": "passed", "seconds": receipt["seconds"]}), flush=True)
            if stage == until:
                break


def certify(repo, directory, apk, aapt, apksigner, certificate):
    plan = verified_plan(directory, repo)
    for stage in STAGES:
        receipt = load(directory / (stage + ".json"))
        if receipt.get("status") != "passed" or receipt.get("plan_sha256") != plan["plan_sha256"]:
            raise ValueError("Missing successful gate: " + stage)
        guard_path = directory / (stage + "-guard-private.json")
        if digest(guard_path) != receipt.get("guard_sha256"):
            raise ValueError("Guard evidence changed: " + stage)
    assembled = load(directory / "apk.json")["apk"]
    if assembled != {"sha256": digest(apk), "bytes": apk.stat().st_size}:
        raise ValueError("APK differs from the guarded assembly output.")
    metadata = run([str(aapt), "dump", "badging", str(apk)], repo, directory / "commands.jsonl", SCRIPT)
    signature = run([str(apksigner), "verify", "--print-certs", str(apk)], repo, directory / "commands.jsonl", SCRIPT)
    if metadata.returncode or signature.returncode:
        raise ValueError("APK metadata/signature verification failed.")
    match = re.search(r"package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", metadata.stdout.decode())
    certs = re.findall(r"Signer #\d+ certificate SHA-256 digest: ([a-fA-F0-9]+)", signature.stdout.decode())
    if not match or [c.lower() for c in certs] != [certificate.lower()]:
        raise ValueError("Unexpected APK metadata or signer certificate.")
    if digest(apk) != assembled["sha256"]:
        raise ValueError("APK changed during verification.")
    if match[1] != "com.henriquesebastiao.downtify.debug":
        raise ValueError("Unexpected package; refuse production variant.")
    current_source = source(repo)
    if current_source["tree_sha256"] != plan["source"]["tree_sha256"]:
        raise ValueError("Source changed during APK verification.")
    manifest = {"schema": 1, "version": match[3], "version_code": int(match[2]), "package": match[1],
                "activity": "com.henriquesebastiao.downtify.MainActivity", "filename": apk.name,
                "sha256": digest(apk), "bytes": apk.stat().st_size, "certificate_sha256": certificate.lower(),
                "source": current_source, "gated_source": plan["source"], "plan_sha256": plan["plan_sha256"],
                "automated_gates": "passed", "physical_tests": "NOT_RUN",
                "created_utc": dt.datetime.now(dt.timezone.utc).isoformat()}
    save(directory / "artifact.json", manifest)
    print(json.dumps(manifest))


def report(directory):
    plan = load(directory / "plan.json")
    rows = []
    for stage in STAGES:
        path = directory / (stage + ".json")
        if not path.exists():
            rows.append({"stage": stage, "recorded_status": "NOT_RUN"})
            continue
        receipt = load(path)
        row = {"stage": stage, "recorded_status": receipt.get("status"),
               "seconds": receipt.get("seconds"), "receipt_sha256": digest(path)}
        guard_path = directory / (stage + "-guard-private.json")
        if receipt.get("status") == "passed":
            if not guard_path.exists() or digest(guard_path) != receipt.get("guard_sha256"):
                row["recorded_status"] = "EVIDENCE_CHANGED"
            else:
                row["memory_peak_bytes"] = load(guard_path).get("memory_peak")
        if "junit" in receipt:
            row["junit"] = receipt["junit"]
        rows.append(row)
    result = {"kind": "historical_build_summary", "label": plan["label"], "source": plan["source"],
              "plan_sha256": plan["plan_sha256"], "stages": rows,
              "source_revalidated": False, "live_process_state_checked": False,
              "physical_tests": "NOT_RUN"}
    save(directory / "report.json", result)
    return result


def main():
    global AUDIT_PATH
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=["plan", "run", "certify", "report"])
    parser.add_argument("--repo", type=Path, default=SCRIPT.parents[2])
    parser.add_argument("--out", type=Path, required=True)
    parser.add_argument("--label", default="candidate")
    parser.add_argument("--until", choices=list(STAGES), default="lint")
    parser.add_argument("--apk", type=Path)
    parser.add_argument("--aapt", type=Path)
    parser.add_argument("--apksigner", type=Path)
    parser.add_argument("--certificate")
    parser.add_argument("--sdk", type=Path, help="Existing Android SDK; never downloaded or modified by this tool")
    args = parser.parse_args()
    repo = args.repo.resolve()
    directory = private_directory(args.out, repo)
    AUDIT_PATH = directory / "commands.jsonl"
    if args.action == "report":
        print(json.dumps(report(directory)))
    elif args.action == "plan":
        if (directory / "plan.json").exists():
            raise ValueError("Plan exists; choose a new output directory.")
        plan = make_plan(repo, args.label)
        save(directory / "plan.json", plan)
        print(json.dumps(plan, indent=2))
    elif args.action == "run":
        execute(repo, directory, args.until, args.sdk)
    else:
        if not all((args.apk, args.aapt, args.apksigner, args.certificate)):
            parser.error("certify requires --apk --aapt --apksigner --certificate")
        certify(repo, directory, args.apk.resolve(), args.aapt.resolve(), args.apksigner.resolve(), args.certificate)


if __name__ == "__main__":
    try:
        main()
    except (ValueError, OSError, subprocess.SubprocessError) as error:
        print(json.dumps({"status": "STOPPED", "reason": str(error)}), file=sys.stderr)
        sys.exit(1)

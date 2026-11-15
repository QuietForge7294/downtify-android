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
    "app-test-compile": [":app:compileDebugUnitTestKotlin"],
    "app-tests": [":app:testDebugUnitTest"],
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


def validate_beta_version(code=None, name=None):
    if code is None and name is None:
        return None
    if code is None or name is None:
        raise ValueError("Provide both beta version code and beta version name.")
    if isinstance(code, bool) or not isinstance(code, int) or not 1 <= code <= 2_100_000_000:
        raise ValueError("Beta version code must be an integer from 1 to 2100000000.")
    if not isinstance(name, str) or not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._+-]{0,79}", name):
        raise ValueError("Beta version name must be 1-80 ASCII version characters.")
    return {"code": code, "name": name}


def plan_beta_version(plan):
    pinned = plan.get("pinned_beta_version")
    if pinned is None:
        return None
    if not isinstance(pinned, dict) or set(pinned) != {"code", "name"}:
        raise ValueError("Invalid pinned beta version in plan.")
    return validate_beta_version(pinned["code"], pinned["name"])


def make_plan(repo, label, allow_dependency_downloads=False, beta_version_code=None, beta_version_name=None):
    if not re.fullmatch(r"[A-Za-z0-9._-]+", label):
        raise ValueError("Use a simple release label.")
    pinned_beta = validate_beta_version(beta_version_code, beta_version_name)
    plan = {"schema": 1, "label": label, "source": source(repo), "stages": STAGES,
            "allow_dependency_downloads": allow_dependency_downloads,
            "pinned_beta_version": pinned_beta,
            "policy": "one guarded Gradle invocation per stage; no retries; USB after final candidate"}
    return dict(plan, plan_sha256=identity(plan))


def verified_plan(directory, repo):
    plan = load(directory / "plan.json")
    unsigned = {k: v for k, v in plan.items() if k != "plan_sha256"}
    if identity(unsigned) != plan["plan_sha256"] or plan["stages"] != STAGES:
        raise ValueError("Plan changed or belongs to another tool version. Prepare a new plan.")
    if not isinstance(plan.get("allow_dependency_downloads", False), bool):
        raise ValueError("Invalid dependency download policy in plan.")
    plan_beta_version(plan)
    if source(repo)["tree_sha256"] != plan["source"]["tree_sha256"]:
        raise ValueError("Source changed. Prepare a new plan; successful receipts cannot be reused.")
    return plan


def test_counts(repo, module="core/player"):
    paths = list((repo / module / "build/test-results/testDebugUnitTest").glob("TEST-*.xml"))
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


def guard_snapshot():
    if not LATEST.exists():
        return None, None
    checksum = digest(LATEST)
    try:
        return checksum, Path(load(LATEST)["receipt"])
    except (OSError, ValueError, KeyError, TypeError):
        return checksum, None


def capture_guard(directory, stage, previous):
    """Retain fresh failed/refused evidence too; never recycle the last run's proof."""
    if not LATEST.exists():
        return {"guard_evidence": "missing"}, None
    if digest(LATEST) == previous[0]:
        return {"guard_evidence": "stale"}, None
    try:
        path = Path(load(LATEST)["receipt"])
        if path == previous[1]:
            return {"guard_evidence": "stale"}, None
        guard = load(path)
        if not isinstance(guard, dict):
            raise ValueError("Invalid guard receipt.")
        output = directory / (stage + "-guard-private.json")
        save(output, guard)
        return {"guard_evidence": "fresh", "guard_sha256": digest(output)}, guard
    except (OSError, ValueError, KeyError, TypeError):
        return {"guard_evidence": "unreadable"}, None


def execute(repo, directory, until, sdk=None, allow_dependency_downloads=False,
            beta_version_code=None, beta_version_name=None):
    if sys.platform != "linux" or not GUARD.is_file():
        raise ValueError("Build execution requires the installed opencode Linux guard.")
    plan = verified_plan(directory, repo)
    if plan.get("allow_dependency_downloads", False) is not allow_dependency_downloads:
        raise ValueError("Dependency download policy differs from plan; use the same explicit choice.")
    pinned_beta = plan_beta_version(plan)
    if pinned_beta != validate_beta_version(beta_version_code, beta_version_name):
        raise ValueError("Beta version differs from plan; use the same explicit version pair.")
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
                network = [] if allow_dependency_downloads else ["--offline"]
                version = [] if pinned_beta is None else [
                    "-PmusicAiBetaVersionCode=" + str(pinned_beta["code"]),
                    "-PmusicAiBetaVersionName=" + pinned_beta["name"],
                ]
                argv = [str(GUARD), "--wrapper", str(repo / "gradlew"), "--", *network, *version, *tasks, "--console=plain"]
                # Prints the full future invocation before running it. SSH callers must also announce it in chat.
                print(json.dumps({"stage": stage, "command": argv}), flush=True)
                receipt = {"stage": stage, "plan_sha256": plan["plan_sha256"], "status": "running", "sdk_path": sdk_path}
                save(receipt_path, receipt)
                previous_guard = guard_snapshot()
                before = time.monotonic()
                try:
                    result = run(argv, repo, directory / "commands.jsonl", SCRIPT,
                                 log=directory / (stage + "-private.log"), timeout=1300)
                    receipt.update(return_code=result.returncode, seconds=round(time.monotonic() - before, 3))
                    evidence, guard = capture_guard(directory, stage, previous_guard)
                    receipt.update(evidence)
                    if result.returncode != 0:
                        raise ValueError("Guard/build failed: exit " + str(result.returncode))
                    if guard is None:
                        raise ValueError("No fresh guard receipt.")
                    if guard.get("status") != "success" or guard.get("return_code") != 0:
                        raise ValueError("Guard receipt does not prove success.")
                    if stage in {"player-tests", "app-tests"}:
                        receipt["junit"] = test_counts(repo, "app" if stage == "app-tests" else "core/player")
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


def signer_certificates(output):
    # SDK 35 says "Signer #1"; SDK 37 uses "V2 Signer:" for the same APK.
    pattern = r"(?m)^(?:Signer #\d+|V[1-4] Signer(?: #\d+)?):? certificate SHA-256 digest: ([a-fA-F0-9]{64})\s*$"
    return [value.lower() for value in re.findall(pattern, output)]


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
    certs = signer_certificates(signature.stdout.decode())
    if not match or [c.lower() for c in certs] != [certificate.lower()]:
        raise ValueError("Unexpected APK metadata or signer certificate.")
    pinned_beta = plan_beta_version(plan)
    if pinned_beta is not None and (int(match[2]) != pinned_beta["code"] or match[3] != pinned_beta["name"]):
        raise ValueError("APK beta version does not match the pinned plan.")
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
        for key in ("return_code", "error_type", "guard_evidence"):
            if key in receipt:
                row[key] = receipt[key]
        guard_path = directory / (stage + "-guard-private.json")
        if receipt.get("status") == "passed" or "guard_sha256" in receipt:
            if not guard_path.exists() or digest(guard_path) != receipt.get("guard_sha256"):
                row["recorded_status"] = "EVIDENCE_CHANGED"
            else:
                guard = load(guard_path)
                row["memory_peak_bytes"] = guard.get("memory_peak")
                row["guard_status"] = guard.get("status")
                row["guard_return_code"] = guard.get("return_code")
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
    parser.add_argument("--allow-dependency-downloads", action="store_true",
                        help="Explicit plan/run opt-in to Gradle dependency downloads; guard and SDK limits stay unchanged")
    parser.add_argument("--beta-version-code", type=int, help="Explicit beta-only versionCode, paired with --beta-version-name")
    parser.add_argument("--beta-version-name", help="Explicit beta-only versionName, paired with --beta-version-code")
    args = parser.parse_args()
    if args.allow_dependency_downloads and args.action not in {"plan", "run"}:
        parser.error("--allow-dependency-downloads applies only to plan and run")
    if (args.beta_version_code is not None or args.beta_version_name is not None) and args.action not in {"plan", "run"}:
        parser.error("Beta version options apply only to plan and run; certification reads the pinned plan")
    try:
        validate_beta_version(args.beta_version_code, args.beta_version_name)
    except ValueError as error:
        parser.error(str(error))
    repo = args.repo.resolve()
    directory = private_directory(args.out, repo)
    AUDIT_PATH = directory / "commands.jsonl"
    if args.action == "report":
        print(json.dumps(report(directory)))
    elif args.action == "plan":
        if (directory / "plan.json").exists():
            raise ValueError("Plan exists; choose a new output directory.")
        plan = make_plan(repo, args.label, args.allow_dependency_downloads, args.beta_version_code, args.beta_version_name)
        save(directory / "plan.json", plan)
        print(json.dumps(plan, indent=2))
    elif args.action == "run":
        execute(repo, directory, args.until, args.sdk, args.allow_dependency_downloads,
                args.beta_version_code, args.beta_version_name)
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

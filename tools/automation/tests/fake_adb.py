"""Synthetic ADB subprocess protocol fixture. Never connects to a device."""
import base64
import json
import os
from pathlib import Path
import sys

path = Path(os.environ["DOWNTIFY_FAKE_ADB_STATE"])
state = json.loads(path.read_text(encoding="utf-8"))
args = sys.argv[1:]
if args[:1] == ["-s"]:
    args = args[2:]
state.setdefault("commands", []).append(args)
package = "com.henriquesebastiao.downtify.debug"
result = b""
code = 0
if args == ["devices", "-l"]:
    result = b"List of devices attached\nsynthetic-device device usb:1-1 product:fixture\n"
elif args == ["shell", "dumpsys", "package", package]:
    result = b"versionCode=25\nversionName=beta\n"
elif args[:4] == ["shell", "dumpsys", "activity", "exit-info"]:
    text = "ACTIVITY MANAGER PROCESS EXIT INFO (dumpsys activity exit-info)\n"
    if state.get("crashed") and args[4] == package:
        text += ("ApplicationExitInfo #0:\n timestamp=2026-11-14 10:07:05.714 pid=12 user=0\n"
                 "process=" + package + " reason=4 (APP CRASH(EXCEPTION))\n")
    else:
        text += "No exit info for package\n"
    result = text.encode()
elif args == ["shell", "dumpsys", "trust"]:
    result = b"deviceLocked=0\n"
elif args == ["shell", "dumpsys", "window", "windows"]:
    result = ("mCurrentFocus=Window{fixture u0 " + state.get("focus", package) + "/MainActivity}\n").encode()
elif args == ["shell", "dumpsys", "media_session"]:
    result = ("package=" + package + "\n state=PlaybackState {state=PAUSED(2), position=12}\nqueue size=0\n").encode()
elif args == ["exec-out", "screencap", "-p"]:
    result = base64.b64decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+j5wAAAABJRU5ErkJggg==")
elif args[:3] == ["shell", "uiautomator", "dump"]:
    if state.get("dump_fails"):
        code = 1
    else:
        state["dump_file"] = args[3]
elif args[:2] == ["exec-out", "cat"]:
    if state.get("dump_file") != args[2]:
        code = 1
    else:
        result = '<hierarchy><node text="18 éléments" enabled="true" bounds="[0,0][100,80]"/></hierarchy>'.encode()
elif args[:3] == ["shell", "rm", "-f"]:
    state.pop("dump_file", None)
elif args[:3] == ["shell", "input", "tap"]:
    if state.get("crash_on_tap"):
        state["crashed"] = True
elif args == ["shell", "input", "keyevent", "4"]:
    pass
else:
    code = 1
path.write_text(json.dumps(state), encoding="utf-8")
sys.stdout.buffer.write(result)
sys.exit(code)
